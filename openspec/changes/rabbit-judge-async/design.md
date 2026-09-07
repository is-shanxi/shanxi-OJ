# 设计：RabbitMQ 异步判题（rabbit-judge-async）

## Context

现状（动机见 proposal.md）：`QuestionSubmitServiceImpl.doQuestionSubmit` 在落库后以 `CompletableFuture.runAsync(() -> judgeService.doJudge(submitId))` 触发判题，未指定执行器 → 运行在 JVM 公共 `ForkJoinPool.commonPool()` 上：守护线程、重启丢任务、排队不可观测、无背压。`JudgeServiceImpl.doJudge` 内部为 check-then-act 状态守卫（非原子）、异常时立即置 FAILED 并 rethrow（异常被 runAsync 吞掉，log.error 是唯一观测点）。`RemoteCodeSandbox` 用 Hutool HTTP 裸调沙箱（**无超时**）。全项目 `doJudge(long)` 仅此一个调用方，后端无任何 `@Transactional`（各语句自动提交，"先落库再发消息"无事务可见性问题）。

改造后的调用链：

```
doQuestionSubmit（校验/save(WAITING)/incrementSubmitNum 均不变）
  → JudgeMessageProducer（按 judge.async.type 路由）
      ├─ thread   → ThreadJudgeProducer：runAsync(doJudge) 原样
      └─ rabbitmq → RabbitJudgeProducer：convertAndSend(Exchange, "judge.submit", submitId)
                     + publisher confirm/return 回调打日志；发送异常吞掉 + log.error
  → [yuoj.judge.exchange] → [yuoj.judge.queue]（durable + DLX/DLK，并发2/prefetch1/手动ack）
  → JudgeMessageConsumer
      ├─ doJudge 正常返回 → basicAck
      ├─ BusinessException（业务已置终态）→ basicAck
      └─ 其他异常 → basicNack(requeue=false) → [yuoj.judge.dlx] → [yuoj.judge.dlq]（人工处置）

SubmitStuckRecoveryTask（@Scheduled，每 5 分钟）
  → WAITING > 5min：经 JudgeMessageProducer 重发（模式无关）
  → RUNNING > 30min：条件更新置 FAILED + log
```

## Goals / Non-Goals

**Goals:**

- 两种触发通道（thread / rabbitmq）共享同一套判题语义，行为唯一，可公平对比压测
- 消息不丢（持久化 + confirm + 恢复任务兜底）、不重复判题（CAS 认领）、不死循环（手动 ack + DLQ）
- 判题终态保证单点收敛在 `doJudge`，`JudgeService` 接口签名不变
- 沙箱调用具备超时与有限重试，消除手动 ack 模式下"挂起占死消费者"的洞

**Non-Goals:** 见 proposal.md（前端轮询/推送、Outbox、TTL 重试队列、判题独立进程、沙箱本体改动等，不再重复）。

## Decisions

**D1 - 双模式经 Producer 接口切换，仿 `codesandbox.type` 惯例。**
`JudgeMessageProducer` 接口（`void submit(long questionSubmitId)`），`ThreadJudgeProducer` / `RabbitJudgeProducer` 两实现，按 `judge.async.type` 用 `@ConditionalOnProperty` 只装配一个。备选：彻底删掉 runAsync（弃——破坏"克隆即跑"，且失去 A/B 对比抓手）；手写工厂类分支（弃——`@ConditionalOnProperty` 更符合 Spring 惯例，项目已有工厂先例但无手写分支必要）。

**D2 - 重试收进判题域（`doJudge` 内部），消费者保持极薄。**（拷问 Q10 的决定）
`doJudge` 用 `RetryTemplate`（spring-retry，SB 2.7.2 管理版本，指数退避 initial 1000ms / multiplier 2 / maxInterval 4000，共 4 次尝试 = 首次 + 3 次重试，累计退避约 7s）只包裹"沙箱调用 + 结果解析"段；耗尽后 `doJudge` 自己置 FAILED 并抛 `BusinessException`。备选：listener 层 RetryInterceptor（弃——终态保证将散布在消费者与线程模式包装器两处，thread 模式必须补 try-catch→FAILED，否则沙箱抖动会让 thread 模式比现状倒退（卡 RUNNING）；开关两侧行为不一致，压测对比不公平）。不引入 spring-retry 而手写循环（弃——退避/恢复器要手造，且 spring-retry 是 SB 生态标准件）。

**D3 - 幂等守卫改为 CAS 条件更新。**
`UPDATE question_submit SET status=1 WHERE id=? AND status=0`（注意 `map-underscore-to-camel-case: false`，列名与属性同名），按影响行数判定认领结果；0 行视为已被认领/已终态，直接返回。备选：保留 check-then-act + 分布式锁（弃——过重）；乐观锁 version 列（弃——需加列，超出范围）。

**D4 - 异常分类以"代码段"为准，而非异常类型表。**
重试只包沙箱调用段——该段抛出的任何异常都视为可重试；判题前后的业务校验（题目/提交不存在等 `BusinessException`）与判题策略内部异常视为非可重试，立即置 FAILED。备选：维护可重试异常类型清单（弃——与 Hutool/RestTemplate 具体异常耦合，代码段划分更稳）。终态保证：`doJudge` 出口要么终态（SUCCEED/FAILED）要么抛出前已置终态；消费者对 `BusinessException` 一律 ack（视为"业务已处理"）。

**D5 - 发布失败吞掉 + log.error，恢复任务兜底。**
备选：异常传播（弃——提交行已落库，用户重试造成重复提交 + submitNum 重复计数）；降级为线程池直判（弃——掩盖故障、双通道语义混乱）。publisher confirm 配 `publisher-confirm-type: correlated` + `publisher-returns: true` + `template.mandatory: true`，confirm-nack/return 均打 log（观测点）。不用 Outbox（Non-goal）。

**D6 - 消息体仅为 `submitId`（Long）。**
消息契约 = 现有 `JudgeService.doJudge(long)` 签名，判题数据由消费侧从 DB 读取。备选：全量 payload（弃——引入第二份消息模型与序列化漂移，背离现有契约）。

**D7 - 拓扑：direct exchange + 单队列 + DLX/DLQ，全持久化。**
命名集中在 `RabbitMQConfig` 常量：exchange `yuoj.judge.exchange`（direct）、queue `yuoj.judge.queue`、routing key `judge.submit`、`yuoj.judge.dlx` / `yuoj.judge.dlq`；队列声明带 `x-dead-letter-exchange`/`x-dead-letter-routing-key` 参数，消息默认持久化。消费者 `AcknowledgeMode.MANUAL`，并发 2、prefetch 1（对齐单沙箱实例容量），DLQ 无消费者（管理台 :15672 人工处置）。备选：topic/fanout exchange（弃——单消息类型，direct 足够）；TTL 重试队列（Non-goal）。

**D8 - 沙箱调用加超时，配置化，属后端调用侧。**
`RemoteCodeSandbox` Hutool 请求加 `setConnectionTimeout` / `setReadTimeout`（配置项挂 `codesandbox.*` 前缀，read 默认 60s > 题目最大 timeLimit）。**沙箱接口签名与 `auth` 鉴权头不变，无两端同步要求**；`ExampleCodeSandbox`（本地进程）不受影响。

**D9 - 恢复任务经 Producer 接口重发，模式无关；RUNNING 置失败必须条件更新。**
`@Scheduled(fixedDelay)` 每 5 分钟：`WAITING` 超 30 分钟重发（经当前配置通道，兼作 thread 模式僵尸救援；重发后的重复消费由 D3 CAS 兜住，幂等安全；**阈值须远大于正常队列积压时长**——压测实测 5 分钟阈值会把"仍在排队的提交"误判为消息丢失而放大积压）；`RUNNING` 超 30 分钟 `UPDATE ... SET status=3 WHERE id=? AND status=1`（条件更新，防覆盖刚完成的 SUCCEED——见 Risks）。备选：重置为 WAITING 重发（弃——与在途消息并发判题产生 ABA 竞态）。

**D10 - `application.yml.example` 默认 `thread`，本地真实配置 `rabbitmq`。**
与"模板可跑、真实口令/配置不入库"的项目约定一致；RabbitMQ 连接段以脱敏注释形式写入 example。

**D11 - 压测资产入库。**
JMeter 测试计划（200 并发 × 60s 双模式各一轮 + 杀沙箱容器故障注入场景）提交到 `oj-backend-master/` 下；`jmeter.log` 加入 `.gitignore`。

## Risks / Trade-offs

- [毒丸消息无限重投] → 手动 ack + 意外异常 `basicNack(requeue=false)` + DLQ 保留人工处置
- [恢复任务覆盖刚完成的结果（RUNNING→FAILED 打回 SUCCEED）] → 恢复任务用条件更新（仅 RUNNING 可置 FAILED）；30min 阈值远大于正常判题时长（read 超时 60s × 重试 3 次 ≈ 3min 上限）
- [双模式行为漂移] → 重试/终态逻辑单点在 `doJudge`（D2/D4），Producer 只负责触发；spec 中两种通道行为一致性为显式 requirement
- [RabbitMQ 成为新单点] → 持久化消息 + confirm；开关回切 thread 即回滚（无 DB migration）；恢复任务在 broker 长时间不可用时以日志暴露
- [重试放大沙箱故障期负载] → 退避 1s/2s/4s、上限 3 次；最坏每消息 4 次调用，并发 2 消费者封顶
- [重试阻塞消费者线程] → 单消息最坏阻塞约 7s（退避累计），并发 2 下可接受；后续可调并发
- [Hutool HTTP 无超时是存量隐患，thread 模式同样受益于 D8] → 超时改动放在调用侧通用生效，不依赖 MQ 模式
- [JVM 与数据库时钟/时区错位（createTime 由 DB DEFAULT CURRENT_TIMESTAMP 写本地墙钟，JDBC 参数因 serverTimezone=UTC 被转 UTC）] → 恢复任务的阈值用 MySQL 自身时钟计算（`createTime < DATE_SUB(NOW(), INTERVAL n MINUTE)`），不传 Java Date 参数

## Migration Plan

1. 启动已有 RabbitMQ 容器（Docker Desktop；exchange/queue 由代码声明自动创建，无需手工建）。
2. 本地 `application.yml` 配置连接信息 + `judge.async.type: rabbitmq`；example 默认 `thread` 保证克隆即跑。
3. 上线顺序无要求：新代码在 `thread` 模式下行为与现状兼容（判题语义增强向后兼容）。
4. 回滚：`judge.async.type` 切回 `thread` 重启即可；滞留队列的消息在重新启用 rabbitmq 后继续消费，或经管理台清空。
5. 无数据库 schema 变更、无接口签名变更（前端 `generated/` 不需重新生成）。

## Open Questions

无——三轮需求拷问（grill）已将动机、方案边界、失败语义、拓扑、验收口径全部收敛。

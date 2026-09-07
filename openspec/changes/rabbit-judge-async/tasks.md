# 任务：RabbitMQ 异步判题（rabbit-judge-async）

## 1. 基础设施与依赖

- [x] 1.1 后端 `pom.xml` 新增 `spring-boot-starter-amqp` 与 `spring-retry` 依赖（SB 2.7.2 版本管理内，不写版本号），执行 `./mvnw compile` 验证依赖解析与编译通过
- [ ] 1.2 `application.yml.example` 增加脱敏注释版 RabbitMQ 连接段（host/port/账号占位 changeme）与 `judge.async.type: thread`（默认线程模式）；本地真实 `application.yml`（不入库）配置 RabbitMQ 连接与 `judge.async.type: rabbitmq`，验证后端以两种模式均可正常启动

## 2. 判题域重构（`doJudge` 语义单点收敛，两模式共享）

- [ ] 2.1 `RemoteCodeSandbox` 的 Hutool HTTP 调用增加配置化 `setConnectionTimeout`/`setReadTimeout`（挂 `codesandbox.*` 配置前缀，read 默认 60s > 题目最大 timeLimit；沙箱接口签名与 auth 头不变）；验证：停掉沙箱服务后调用在超时时间内抛异常而非永久阻塞
- [ ] 2.2 `JudgeServiceImpl.doJudge` 幂等守卫改为 CAS 条件更新（`UPDATE status=1 WHERE id=? AND status=0`，注意列名与属性同名；0 行影响视为已被认领，直接返回 null/跳过）；验证：同一提交并发触发两次判题，仅一次执行、状态与计数正常
- [x] 2.3 `doJudge` 沙箱调用段用 `RetryTemplate` 包裹（指数退避 1s/2s/4s，共 4 次尝试 = 首次 + 3 次重试），重试期间不置终态；耗尽后 `doJudge` 置 FAILED 并抛 `BusinessException`，非可重试业务异常立即置 FAILED；保持 `JudgeService.doJudge(long)` 接口签名不变；验证：停沙箱容器提交后约 7 秒内落 FAILED（status=3）且日志含完整异常链，瞬时恢复场景自愈到 status=2

## 3. MQ 通道（`mq/` 包）

- [ ] 3.1 新增 `RabbitMQConfig`：常量化拓扑命名（exchange `yuoj.judge.exchange`、queue `yuoj.judge.queue`、routing key `judge.submit`、`yuoj.judge.dlx`/`yuoj.judge.dlq`），声明 direct exchange、durable 队列（含 `x-dead-letter-exchange`/`x-dead-letter-routing-key` 参数）与绑定；验证：启动后 RabbitMQ 管理台（:15672）可见全部拓扑且队列 durable
- [ ] 3.2 新增 `JudgeMessageProducer` 接口与 `ThreadJudgeProducer`（现有 runAsync 逻辑原样搬入）、`RabbitJudgeProducer`（发送 submitId、消息持久化、confirm/return 回调打日志、发送异常吞掉 + log.error），用 `@ConditionalOnProperty` 按 `judge.async.type` 装配；`QuestionSubmitServiceImpl.doQuestionSubmit` 将 `CompletableFuture.runAsync(...)` 替换为接口调用；验证：thread 模式提交→判题全链路回归无变化，rabbitmq 模式提交后队列出现消息且判题落终态
- [ ] 3.3 新增 `JudgeMessageConsumer`：监听 `yuoj.judge.queue`，`AcknowledgeMode.MANUAL`、并发 2、prefetch 1；`doJudge` 正常返回或抛 `BusinessException` 时 `basicAck`，其他异常 `basicNack(requeue=false)` 进 DLQ；验证：正常判题消息被确认移除，投递一条损坏消息后可在 DLQ 中看到且主队列无堆积、无重复投递

## 4. 卡死恢复任务

- [x] 4.1 新增 `job/SubmitStuckRecoveryTask`（`@Scheduled` 每 5 分钟）：WAITING 超过 30 分钟的提交经 `JudgeMessageProducer` 接口重发（阈值需远大于正常队列积压时长，防止把排队中的提交误判为消息丢失）（模式无关）；RUNNING 超过 30 分钟的提交用条件更新（仅 RUNNING 可置）置 FAILED 并记录日志；验证：手工插入一条超龄 WAITING 提交后 5 分钟内被重新触发判题，一条超龄 RUNNING 提交被置 FAILED，而中途判题完成（SUCCEED）的提交不被覆盖

## 5. 验证与压测

- [x] 5.1 双模式功能回归：同一题目同一代码分别在 thread / rabbitmq 模式提交，判题终态、judgeInfo、submitNum/acceptedNum 计数口径一致，通过率展示正常
- [x] 5.2 故障注入：rabbitmq 模式下停掉沙箱容器，确认重试耗尽后 FAILED、无消息死循环（主队列清空），恢复沙箱后新提交正常判题；停掉 RabbitMQ 容器提交代码，确认接口不报错、提交行停留 WAITING、恢复 broker 后由恢复任务重新触发判题
- [x] 5.3 JMeter 测试计划（200 并发 × 60 秒，thread 与 rabbitmq 各一轮）落库到 `oj-backend-master/`，执行并记录对比结果：接口无 5xx、全部提交行到达终态（零永久 WAITING/RUNNING）、队列深度归零；`jmeter.log` 加入 `.gitignore`
- [ ] 5.4 文档同步：`TECH_DESIGN.md` 更新判题调用链图与数据流（Producer/Consumer/恢复任务），`AGENTS.md` 本地运行说明补 RabbitMQ 依赖（Docker 容器启动 + 管理台端口）

## 6. 收尾

- [ ] 6.1 `openspec validate rabbit-judge-async` 通过，全量构建（后端 `./mvnw package`）通过，按中文 conventional commits 提交（如 `feat: RabbitMQ 异步判题——双模式切换、可靠投递与卡死恢复`）

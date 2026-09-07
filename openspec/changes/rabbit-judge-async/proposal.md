# 提案：RabbitMQ 异步判题（rabbit-judge-async）

## Why

当前判题异步化依赖 `CompletableFuture.runAsync()` 的默认执行器 `ForkJoinPool.commonPool()`：JVM 全局共享、守护线程、无持久化——JVM 重启时未执行的判题任务直接蒸发（提交永久卡 WAITING）、排队点不可观测、无削峰与可靠投递语义，判题异常的观测点也只有一处 log。引入 RabbitMQ 把"判题排队"从隐式的公共线程池显式化为持久化队列，获得削峰、可靠投递、死信处置与队列深度可观测能力。动机为探索对比（现有 JMeter 压测下系统无异常，目标是观察 MQ 形态下的行为差异）+ 教学价值；因此改造采用**双模式可切换**设计：线程池模式保留（克隆即跑，不装 RabbitMQ 也能用），RabbitMQ 模式为本Capability的主链路，两种模式行为一致（同一套重试与终态语义），保证对比公平。

## What Changes

- **依赖**：后端新增 `spring-boot-starter-amqp`、`spring-retry`。
- **新增 `mq/` 包**（后端）：
  - `RabbitMQConfig`：声明 direct exchange `yuoj.judge.exchange`、队列 `yuoj.judge.queue`（durable、持久化消息、配置 DLX/DLK 参数）、死信交换机 `yuoj.judge.dlx` 与死信队列 `yuoj.judge.dlq`；routing key `judge.submit`。
  - `JudgeMessageProducer` 接口 + 两个实现：`ThreadJudgeProducer`（现有 `runAsync` 逻辑原样搬入）、`RabbitJudgeProducer`（发送 `submitId`，publisher confirm 回调打日志）；按配置 `judge.async.type = thread | rabbitmq` 路由（工厂模式，与 `codesandbox.type` 惯例一致）。
  - `JudgeMessageConsumer`：监听 `yuoj.judge.queue`，手动 ack。`doJudge` 正常返回或抛出"业务已置终态"的 `BusinessException` → ack；意外异常 → nack（requeue=false）→ 进 DLQ，由管理台人工处置。并发 2、prefetch 1（单沙箱实例容量对齐）。
- **`QuestionSubmitServiceImpl.doQuestionSubmit`**：将 `CompletableFuture.runAsync(...)` 一行替换为调用 `JudgeMessageProducer` 接口；提交计数等前置逻辑不变。
- **`JudgeServiceImpl.doJudge` 重构**（两种模式共享，行为唯一）：
  - 幂等守卫由 check-then-act 改为 **CAS 条件更新**（`UPDATE ... SET status=RUNNING WHERE id=? AND status=WAITING`，0 行即视为已被认领，直接返回）。
  - 沙箱调用用 `RetryTemplate` 包裹：可重试异常（HTTP 失败/超时/沙箱 5xx）按指数退避重试 3 次（1s/2s/4s）；**重试期间不置终态**；耗尽后由 `doJudge` 自己置 FAILED 并抛 `BusinessException`（终态保证单点）。
  - 非可重试异常（题目不存在等业务错误）→ 置 FAILED → 抛。
- **`RemoteCodeSandbox`**：HTTP 调用增加 connection/read 超时（配置化，read 超时大于题目最大 timeLimit）——消除"沙箱挂起 → 判题线程永久阻塞 → 消息永不 ack"的隐患，是手动 ack 语义的前置依赖。
- **新增 `job/SubmitStuckRecoveryTask`**：每 5 分钟扫描——WAITING 超过 5 分钟的提交经 `JudgeMessageProducer` 接口重发（模式无关，兼作线程模式下的僵尸救援）；RUNNING 超过 30 分钟的置 FAILED 并记录日志。
- **配置**：`application.yml.example` 补 RabbitMQ 连接段（脱敏）+ `judge.async.type`（默认 `thread`，保证克隆即跑）；本地真实 `application.yml` 配 `rabbitmq`。
- **文档**：TECH_DESIGN.md 判题链路图与调用链更新、AGENTS.md 本地运行说明补 RabbitMQ 依赖。
- **压测**：JMeter 测试计划（.jmx）入库，`jmeter.log` 从仓库根移出（加入 .gitignore）。

**无变更**：数据库 schema、后端对外接口签名（`generated/` 无需重新生成）、前端、沙箱服务本体、判题策略与沙箱工厂/代理结构。

## Capabilities

### New Capabilities

- `backend/judge-async`：异步判题触发与可靠性能力——判题任务经可配置异步通道（线程池 / RabbitMQ）触发；消息内容为 submitId；发布端 publisher confirm 与发送失败兜底；消费端手动 ack + 死信队列；幂等消费（CAS 认领）；沙箱调用超时与重试；卡死恢复任务。判题核心入口仍为 `JudgeService.doJudge(long)`，消费端是唯一新增触发方。

### Modified Capabilities

- `backend/question-acceptance-rate`：「判题失败状态」requirement 的达成路径变化——由"异常立即置 FAILED"改为"可重试异常重试耗尽后置 FAILED，非可重试异常立即置 FAILED"；"不得永久停留判题中"的目标不变，且新增"重试期间同一提交不得被并发重复判题（CAS 认领），acceptedNum 不因消息重投递而重复自增"的口径。提交计数与通过计数的 requirement 不变。

## Impact

- **后端**（`oj-backend-master/oj-backend-master`）：`pom.xml`；`service/impl/QuestionSubmitServiceImpl`；`judge/JudgeServiceImpl`、`judge/codesandbox/impl/RemoteCodeSandbox`；新增 `mq/` 包（配置、Producer×2、Consumer）与 `job/SubmitStuckRecoveryTask`；`resources/application.yml.example`。
- **依赖**：新增 `spring-boot-starter-amqp`、`spring-retry`（SB 2.7.2 版本管理内）。
- **本地运维**：新增 RabbitMQ 依赖（用户已有 Docker Desktop 容器，启动即可）；`thread` 模式下无新依赖。
- **数据库**：无 schema 变更。
- **前端 / 沙箱服务**：零改动。

## Non-goals（本次不做的内容）

- 前端判题状态轮询 / WebSocket / SSE 推送（用户仍需手动刷新提交列表）。
- Outbox 模式 / 事务性发件箱（发布失败靠卡死恢复任务兜底）。
- TTL 延迟重试队列（重试为消费者内存态，指数退避）。
- 判题独立进程 / 微服务拆分（消费端仍在单体后端进程内）。
- 沙箱服务本体改动（超时加在后端调用侧）；`unsafe/` 沙箱测试代码。
- 其他消息中间件（Kafka / RocketMQ）。

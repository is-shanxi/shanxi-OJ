# AGENTS.md

鱼皮 YuOJ 在线评测系统（教学项目）。三个独立进程：前后端分离 + 独立代码沙箱服务。

## 目录结构（注意：模块目录是双层嵌套同名）

- `oj-backend-master/oj-backend-master/` — 单体后端。Spring Boot 2.7.2 + Java 8，MyBatis Plus，端口 8121，context-path `/api`，包名 `com.yupi.yuoj`
- `oj-code-sandbox-master/oj-code-sandbox-master/` — 代码沙箱。Spring Boot，端口 8090，包名 `com.yupi.yuojcodesandbox`，包 `unsafe/` 是故意用来测试沙箱安全的恶意代码，勿"修复"
- `oj-frontend-master/yuoj-frontend-master/` — 前端。Vue CLI 5 + Vue 3 + TS + Arco Design + Vuex，Monaco 编辑器，默认端口 8080

## 常用命令

```bash
# 后端（目录：oj-backend-master/oj-backend-master）
./mvnw spring-boot:run          # Windows 用 mvnw.cmd
./mvnw package                  # 构建

# 代码沙箱（目录：oj-code-sandbox-master/oj-code-sandbox-master）
./mvnw spring-boot:run

# 前端（目录：oj-frontend-master/yuoj-frontend-master）
npm run serve                   # 开发
npm run build                   # 构建
npm run lint                    # ESLint（含 prettier 规则）
```

## 配置与环境（重要）

- `**/application.yml`、`**/application-*.yml` 均被 .gitignore 排除；本地配置从同级 `*.yml.example` 模板复制并填入真实口令，**任何真实口令不入库**（README 里的示例口令已脱敏为 changeme）。
- 后端依赖 MySQL（库名 `yuoj`，建表脚本 `sql/create_table.sql`）、Redis（session 存储需要 Redis 可用）；`judge.async.type = rabbitmq` 时还需 RabbitMQ（本地用 Docker 容器 `rabbitmq:4-management`，5672/15672；4.x 无默认 guest 用户，需自行 `rabbitmqctl add_user` + 授权 vhost `/`）。
- 后端 `codesandbox.type` 配置决定沙箱实现：`example`（本地进程）/ `remote`（HTTP 调 8090 沙箱，默认）/ `thirdParty`。
- 判题异步通道由 `judge.async.type` 决定：`thread`（JVM 线程池，example 模板默认，克隆即跑）/ `rabbitmq`（RabbitMQ 消息队列）。两通道判题语义一致，切换只影响触发方式。

## 跨服务调用链

前端 →(HTTP + cookie session)→ 后端 :8121/api →(HTTP，固定请求头 `auth: secretKey`)→ 沙箱 :8090 的 `POST /executeCode`。改动任一端的接口签名或鉴权头时必须同步另一端。

判题域在后端 `judge/` 包 + `mq/` 包（异步触发），改动前先看现有结构：

- 提交后经 `mq/JudgeMessageProducer` 接口异步触发判题（`ThreadJudgeProducer` / `RabbitJudgeProducer` 按 `judge.async.type` 装配），消费端 `mq/JudgeMessageConsumer` 手动 ack，死信进 `yuoj.judge.dlq`（管理台人工处置）
- `JudgeServiceImpl` → `JudgeManager` → `CodeSandboxFactory`（按 type 选实现，HTTP 超时配置化）→ `CodeSandboxProxy`（静态代理，统一加 auth 头）；CAS 认领（WAITING→RUNNING 条件更新）保证幂等，重试（RetryTemplate 1s/2s/4s）与终态保证单点收敛在 `doJudge`
- `job/SubmitStuckRecoveryTask` 每 5 分钟兜底：WAITING>5min 重发（阈值用 MySQL `NOW()` 计算，勿改回 Java Date——JDBC `serverTimezone=UTC` 会造成 8 小时错位）、RUNNING>30min 条件置 FAILED
- 判题策略：`JudgeContext` + `JudgeStrategy`（`JavaLanguageJudgeStrategy` / `DefaultJudgeStrategy`），新增语言判题时加策略类而非改默认策略
- 沙箱侧用模板方法：`JavaCodeSandboxTemplate` → `JavaNativeCodeSandbox` / `JavaDockerCodeSandbox`（沙箱把判题输入作为**程序参数**传入，不是 stdin）

## 代码约定

后端（`com.yupi.yuoj`）：

- 标准分层 controller → service/impl → mapper；model 下分 dto/entity/vo/enums
- 统一返回 `BaseResponse`，用 `ResultUtils.success/error` 封装，错误码用 `ErrorCode`；异常抛 `BusinessException`，由全局异常处理器兜底
- 权限校验用 `@AuthCheck` 注解 + AOP（`annotation/` + `aop/` 包），不要在 controller 里手写权限判断
- 逻辑删除字段 `isDelete`（MyBatis Plus 全局配置）；注意 `map-underscore-to-camel-case: false`，即数据库列名与实体属性同名驼峰

前端（`yuoj-frontend-master`）：

- `generated/` 是 openapi-typescript-codegen 从后端 Swagger 自动生成的客户端，**不要手改**。后端接口变更后重新生成：
  `npx openapi --input http://localhost:8121/api/v2/api-docs --output ./generated --client axios`
- 后端地址统一在 `src/main.ts` 里设 `OpenAPI.BASE`（当前 8121）
- axios 全局 `withCredentials = true`（`src/plugins/axios.ts`），登录态靠 cookie session
- 权限控制走 `src/access/`（`AccessEnum` + `checkAccess`），路由守卫在 `src/router/index.ts`

## 本地文档（不入库，改动敏感区前先读）

- `TECH_DESIGN.md` — 三个项目的架构设计文档（含 Mermaid 图、调用链、数据流），最权威
- `ANALYSIS.md` — 原始架构分析报告；注意其中提到的第 4 个微服务项目 `oj-backend-microservice-master/` 已被移除，当前仓库只有上述 3 个模块

## OpenSpec 工作流（新功能开发入口）

- 新功能 / 较大改动一律走 OpenSpec 流程：`/opsx:propose <kebab-case 名>` 创建 change → 人工评审 artifacts → `/opsx:apply <名>` 实现 → `/opsx:archive <名>` 归档；立项前可先 `/opsx:explore` 纯讨论。
- propose 阶段只产出规划文档（proposal / specs 增量 / design / tasks，位于 `openspec/changes/<名>/`），**不改业务代码**；实现必须显式从 apply 开始。
- `openspec/specs/` 是能力规格的长期归宿（归档时自动合并 delta）；`TECH_DESIGN.md` 仍是架构参考文档。
- capability 目录按模块前缀组织：`backend/…` / `sandbox/…` / `frontend/…`；涉及后端↔沙箱接口签名或 auth 头的改动，spec 中必须写明两端同步要求。
- 操作细节以 `.zcode/skills/openspec-*` 技能与 `openspec instructions` 输出为准；`openspec/` 与 `.zcode/` 应提交入库，勿加进 .gitignore。

## 其他坑

- `target/`、`dist/`、`node_modules/`、`tmpCode/` 均为产物，勿提交；沙箱运行时会在 `tmpCode/` 下落用户代码
- 提交信息风格为中文 conventional commits（如 `feat: 单体后端…`、`chore: …脱敏`）
- 文件头常有 `@author liyupi` 模板遗留注释，保持原样即可，不必批量清理
- 命令行编译需 `JAVA_HOME=C:/Program Files/Java/jdk1.8.0_202`（项目 Java 8 + 旧 Lombok；默认 JAVA_HOME 的 JDK 21 会报 `JCTree$JCImport` 编译错）；沙箱进程还要把 JDK8 的 bin 前置到 PATH，否则 javac/java 版本不一致会产出 `UnsupportedClassVersionError`

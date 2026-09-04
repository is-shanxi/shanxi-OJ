# 设计：题目通过率计数（add-question-acceptance-rate）

## Context

现状是"壳全有、芯没有"：`question` 表已有 `submitNum`/`acceptedNum` 列（int not null default 0），`Question`/`QuestionVO` 字段齐备并自动透传，前端列表页已实现通过率展示与配色分级——但全后端没有任何代码写这两个计数器。判题链路（`JudgeServiceImpl.doJudge`）中：判题异步执行（`CompletableFuture.runAsync`）、无异常兜底、`status=2` 仅表示"判题完成"，是否通过只能靠 `judgeInfo.message == "Accepted"`（`JudgeInfoMessageEnum.ACCEPTED` 的 value）判定。另有一处存量 bug：`JudgeServiceImpl:100` 用 `questionId` 查提交表。本项目的关键配置约束：`map-underscore-to-camel-case: false`（数据库列名与实体属性同名，均为驼峰）。

动态与沙箱的接口签名、鉴权头本次均不变，不涉及后端↔沙箱两端同步要求。

## Goals / Non-Goals

**Goals:**

- 让已有通过率展示变为真实数据：提交/判题两处原子计数 + 每日校准收敛
- 判题异常可观测（FAILED 状态 + 日志），消灭"永久判题中"
- 历史数据零脚本回填（校准首跑即回填）

**Non-Goals:**

- 见 proposal 的 Non-goals 一节（详情页展示、排序、按人去重、手动校准接口、重判回退、多实例调度锁等）。补充一条设计级边界：不重构 `QuestionSubmitStatusEnum` 的语义（不拆分"判题完成"与"判题通过"两个概念）。

## Decisions

### D1 计数策略：冗余计数器（而非查询时聚合）

按提交次数口径，读路径（题目分页列表）每次都渲染通过率；聚合方案要么每次分页 `GROUP BY` 全表提交（读放大），要么丢弃已就位的列与前端逻辑。计数器方案与现有 schema/VO/前端完全契合，写路径只多两条单行 UPDATE。

### D2 submitNum 在提交时 +1（而非判题完成时）

提交即计数与"按提交次数"口径自洽：用户确实提交了，哪怕判题崩了（此后会被置 FAILED）也是一次提交。且挂点只有一处（`doQuestionSubmit`），与判题逻辑零耦合。备选"判题完成时"会让 WAITING 期间的提交暂不入账、并把两个计数器耦合进同一个方法，被否决。

### D3 "通过"判据：比对 `JudgeInfoMessageEnum.ACCEPTED.getValue()`

判题主链路中 `doJudge` 拿到策略返回的 `JudgeInfo` 对象，直接 `ACCEPTED.getValue().equals(judgeInfo.getMessage())`，无需二次反序列化；校准任务侧（离线批处理）则从 DB 读 `judgeInfo` JSON 后用 hutool 解析再比对同一枚举。**约束：两处必须引用同一个枚举常量，禁止硬编码字符串**；改动 `JudgeInfoMessageEnum.ACCEPTED` 的 value 会同时静默破坏两处，评审时需重点检查。备选"给 question_submit 加 result 布尔列"被否决：无 schema 变更是本次的明确边界，字符串判据来自自家枚举产出，可控。

### D4 自增实现：`UpdateWrapper.setSql` 数据库端原子自增

`update(new UpdateWrapper<Question>().eq("id", questionId).setSql("submitNum = submitNum + 1"))`。判题是并发异步的，"读出→加一→updateById"会丢失更新，必须单语句自增。注意本项目 `map-underscore-to-camel-case: false`，setSql 里写驼峰列名（`submitNum`）恰好与实体属性一致。自增方法收敛在 `QuestionService`（`incrementSubmitNum` / `incrementAcceptedNum`），供提交链路与判题链路复用。

### D5 一致性：不包事务，漂移由校准收敛

status 更新与 acceptedNum 自增之间崩溃最多造成 ±1 漂移，这是选择"计数器+校准"方案时就接受的代价。反之，若给 `doJudge` 包 `@Transactional`，事务边界会横跨沙箱 HTTP 调用（长事务占连接池），是负优化。两条写路径均保持无事务。

### D6 校准任务：`@Scheduled` 每日 04:00 全量重算

- 启动类加 `@EnableScheduling`，新增 `job/QuestionCountCalibrationTask`，cron `0 0 4 * * ?`。
- 逻辑：查全部未删除题目 → 查全部未删除提交（按 questionId 分组，Java 侧解析 judgeInfo）→ 逐题比对 `submitNum`/`acceptedNum` → 不一致则覆盖写回，并用 log 记录题目 id、期望值、实际值。
- 通过判定：`status=2` 且 `judgeInfo.message == ACCEPTED.getValue()`；judgeInfo 为空/解析失败 → 计提交数不计通过数（与 spec 口径一致）。
- 教学量级（万级提交）全量扫描无压力，单实例部署无并发调度问题。备选"管理员手动触发接口"与"SQL 脚本回填"均列入 Non-goals——校准首跑天然覆盖回填需求。

### D7 判题异常兜底：catch 后置 FAILED + 记日志再抛出

`doJudge` 的沙箱调用与判题过程包入 try/catch：异常时 `log.error` 带提交 id，置 `status=FAILED(3)`（启用现有未用枚举值，前端已支持展示），再抛出原异常。`CompletableFuture.runAsync` 会吞掉异常栈，日志是唯一观测点，必须有。finally 不做状态复位——成功路径已有独立的 status 更新。

### D8 存量修复：`JudgeServiceImpl:100` 改为 `getById(questionSubmitId)`

该方法本次必然动刀，顺手修正传参错误（当前拿题目 id 查提交表，返回错误记录）。该返回值目前无调用方消费，属于无行为风险的纯修正，不单独立 capability。

### D9 前端：仅改 `QuestionSubmitView.vue` 的 `STATUS_TEXT[2]` 为"判题完成"

一行改动。颜色分级逻辑不动（2 仍绿色、3 红色）。用 judgeInfo 渲染完整判果属于功能增强，Non-goal。

## Risks / Trade-offs

- [字符串判据脆弱：改 `ACCEPTED` 枚举 value 会静默破坏计数] → D3 约束两处引用同一常量并在 tasks 中设检查项；枚举 value 由本系统自产，变更频率极低。
- [漂移窗口最长 24h：两步写库非原子、校准每日一次] → 接受（教学项目）；校准日志让漂移可见。
- [校准"先算快照后覆盖写"与实时自增并发时，窗口期内的计数可能被抹掉] → 次日校准自愈，接受；不为这点概率加 WHERE 守卫或锁。
- [校准全表扫 question_submit] → 教学量级可接受；提交表已有 `idx_questionId`，将来量大可加分页批读。
- [`@Scheduled` 单实例假设] → 多实例部署需分布式锁或换调度中间件，已列 Non-goal。
- [判题异常路径的日志是唯一观测点（runAsync 吞异常）] → D7 强制 log.error 带上下文。
- [计数器与逻辑删除：题目逻辑删除后其计数器不再被校准触碰] → 校准只遍历未删除题目，被删题目的提交不参与任何题目统计，口径自洽。

## Migration Plan

无 schema 变更、无接口变更：部署即生效。上线后首个校准周期（04:00）自动完成历史回填；如需立即生效可在测试环境临时把 cron 调密。回滚 = 还原代码即可，计数器列保留最后写入值，无脏结构残留。

## Open Questions

（无——口径、时机、校准形态、失败路径、事务边界、范围均已在探索阶段与需求方对齐。）

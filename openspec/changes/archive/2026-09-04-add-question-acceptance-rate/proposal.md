# 提案：题目通过率计数（add-question-acceptance-rate）

## Why

题目列表页已经展示"通过率"列，数据库 `question` 表也已有 `submitNum` / `acceptedNum` 两列，但全后端没有任何代码维护这两个计数器——它们永远是 0，通过率永远显示"—"。需要把计数器的维护接线到提交/判题链路，让已有的展示变成真实数据。

## What Changes

- **提交计数**：`QuestionSubmitServiceImpl.doQuestionSubmit` 在提交记录插入成功后，对 `question.submitNum` 做数据库端原子自增（`UPDATE ... SET submitNum = submitNum + 1`）。
- **通过计数**：`JudgeServiceImpl.doJudge` 在判题完成后，若 `judgeInfo.message` 等于 `JudgeInfoMessageEnum.ACCEPTED` 的 value（`"Accepted"`），对 `question.acceptedNum` 做原子自增；不自增 `submitNum`（提交时已计）。
- **判题失败兜底**：`JudgeServiceImpl.doJudge` 用 try/catch 兜底，沙箱或判题过程抛异常时将提交置为 `status=FAILED(3)`（启用现有但从未使用的枚举值）并记录日志后抛出，消灭"永久判题中"的僵尸提交。
- **存量 bug 修复**：`JudgeServiceImpl:100` 的 `questionSubmitService.getById(questionId)` 传错主键（拿题目 id 查提交表），改为 `getById(questionSubmitId)`。
- **每日校准任务**：新增 `@Scheduled` 定时任务（需在启动类加 `@EnableScheduling`），每日凌晨全量统计 `question_submit`（Java 侧解析 `judgeInfo` JSON），按题目比对计数器，漂移则覆盖写回并记录日志；首跑即完成历史数据回填。
- **前端文案修正**：`QuestionSubmitView.vue` 的 `STATUS_TEXT[2]` 由"通过"改为"判题完成"（status=2 仅表示判题流程完成，答错/超时也是 2，原文案误导）。
- 无数据库列变更、无沙箱改动、无接口签名变化（`generated/` 不需重新生成，前端展示逻辑不需改动）。

**口径约定**：通过率 = 按提交次数计（`acceptedNum / submitNum`），同一用户多次提交不去重；脏 `judgeInfo` / 判题失败的提交计入分母、不计入分子。

## Capabilities

### New Capabilities

- `backend/question-acceptance-rate`：题目通过率计数能力——提交时原子自增 submitNum、判题通过时原子自增 acceptedNum、判题异常置 FAILED、每日全量校准任务保证计数器与提交流水最终一致。
- `frontend/judge-status-display`：判题状态在前端的展示语义——status=2 展示为"判题完成"而非"通过"，状态文案与后端枚举语义一致。

### Modified Capabilities

（无——`openspec/specs/` 当前为空，本次全部为新建能力）

## Impact

- **后端**（`oj-backend-master/oj-backend-master`）：
  - `service/QuestionService` + `impl/QuestionServiceImpl`：新增原子自增方法
  - `service/impl/QuestionSubmitServiceImpl`：提交后自增 submitNum
  - `judge/JudgeServiceImpl`：通过判定 + acceptedNum 自增 + try/catch 兜底 + bug 修复
  - `MainApplication`：加 `@EnableScheduling`
  - 新增定时校准任务类（`job/` 包）
- **前端**（`yuoj-frontend-master`）：`src/views/question/QuestionSubmitView.vue` 一行文案
- **数据库**：无 schema 变更；历史数据靠校准首跑回填
- **沙箱服务**：无影响
- **接口契约**：无变化；计数器字段不可经现有增删改接口覆写（请求 DTO 不含这两个字段，已核实）

## Non-goals（本次不做的内容）

- 题目详情页展示通过率；列表按通过率排序
- 按人去重的通过率口径（"做出该题的人数 / 提交过该题的人数"）
- 按语言、按时间维度的细分统计
- 管理员手动触发校准的接口（`POST /question/calibrate` 之类）
- 用 judgeInfo 渲染完整判题结果（"答案错误 / 超时 / 内存溢出…"）
- 重判功能及其计数回退逻辑
- `QuestionSubmitStatusEnum` 语义重构（拆分"判题完成"与"判题通过"）
- 多实例部署下的定时任务分布式锁（当前按单实例假设）
- 修复其他与计数无关的存量问题（如 `JudgeInfoMessageEnum` 的 text/value 错位、`QuestionController` 注释错别字等）

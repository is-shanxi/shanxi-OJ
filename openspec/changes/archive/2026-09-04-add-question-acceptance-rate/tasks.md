## 1. 后端计数基础

- [x] 1.1 在 `QuestionService`/`QuestionServiceImpl` 新增原子自增方法 `incrementSubmitNum(long questionId)` 与 `incrementAcceptedNum(long questionId)`：用 `UpdateWrapper.setSql("submitNum = submitNum + 1")` 实现（注意驼峰列名，`map-underscore-to-camel-case: false`）。验证：`mvnw.cmd compile` 通过，且调用后 SQL 为单条 `UPDATE ... SET x = x + 1`（日志确认）
- [x] 1.2 在 `QuestionSubmitServiceImpl.doQuestionSubmit` 的 save 成功后调用 `incrementSubmitNum`。验证：登录后向任一题目提交代码，查库确认该题 `submitNum` 加 1，提交后再查一次不再变化

## 2. 判题链路接线与存量修复

- [x] 2.1 修复 `JudgeServiceImpl:100`：`questionSubmitService.getById(questionId)` 改为 `getById(questionSubmitId)`。验证：判题正常结束后方法返回的记录 id 与本次提交 id 一致
- [x] 2.2 为 `JudgeServiceImpl.doJudge` 的沙箱调用与判题过程加 try/catch 兜底：异常时 `log.error`（带 questionSubmitId）后将提交置为 `QuestionSubmitStatusEnum.FAILED`（status=3）再抛出原异常。验证：停掉代码沙箱后提交代码，该提交状态变为 3 而非永久"判题中"，后端日志有异常记录
- [x] 2.3 判题完成后，若 `JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage())` 则调用 `incrementAcceptedNum`（引用枚举常量，禁止硬编码 `"Accepted"`）。验证：提交一段正确代码后 `acceptedNum` 加 1；提交一段错误代码（Wrong Answer）后 `acceptedNum` 不变

## 3. 每日校准任务

- [x] 3.1 在 `MainApplication` 加 `@EnableScheduling`。验证：应用启动无报错且出现 Scheduling bean 初始化日志
- [x] 3.2 新增 `job/QuestionCountCalibrationTask`（cron `0 0 4 * * ?`）：查全部未删除题目与全部未删除提交，Java 侧按 questionId 分组并解析 judgeInfo JSON（hutool），统计提交数与通过数（status=2 且 message==ACCEPTED 值；judgeInfo 空/解析失败计入提交数不计通过数），与计数器比对，不一致则覆盖写回并 `log.info` 记录题目 id、期望值、实际值。验证：`mvnw.cmd compile` 通过，代码走查确认统计口径与 specs/backend/question-acceptance-rate 一致
- [x] 3.3 回填与收敛验证：手工把某题 `submitNum`/`acceptedNum` 改脏（如置 0 或 +5），临时把 cron 调密触发一次校准，确认计数器恢复为与提交流水一致且日志记录了漂移。验证后把 cron 还原为 `0 0 4 * * ?`

## 4. 前端文案

- [x] 4.1 修改 `src/views/question/QuestionSubmitView.vue` 的 `STATUS_TEXT[2]`：`"通过"` → `"判题完成"`（颜色分级逻辑不动）。验证：`npm run lint` 通过；提交一次错误代码，提交记录页该记录显示绿色"判题完成"而非"通过"

## 5. 整体验证

- [x] 5.1 全链路回归：两个用户同时向同一题目并发提交多次，确认 `submitNum` 增量等于提交记录数、无丢失更新；题目列表页通过率显示真实值（含 `n/m` 数字）；判题异常的提交在列表显示"失败"。验证：数据库核对 + 页面截图/观察
- [x] 5.2 约束检查：全仓库检索确认 `"Accepted"` 判据只通过 `JudgeInfoMessageEnum.ACCEPTED` 常量引用（判题与校准两处）；确认本次无接口签名变化——`generated/` 无需重新生成，前后端契约不变。验证：grep 结果 + git diff 仅含预期文件

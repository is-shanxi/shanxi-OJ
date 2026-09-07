# backend/question-acceptance-rate Specification

## Purpose
让题目通过率从永远为 0 的死数据变成真实可信的统计：在提交与判题链路上维护 question 表的提交数（submitNum）/ 通过数（acceptedNum）计数器，并提供每日校准任务保证计数器与提交流水最终一致。通过率口径为按提交次数计（不去重按人）。

## Requirements

### Requirement: 提交计数

每次成功创建题目提交记录后，系统必须对该题目的 submitNum 执行数据库端原子自增（+1）。计数发生在提交创建时，与判题结果和判题是否完成无关。

#### Scenario: 提交即计数
- **WHEN** 用户对某题目提交代码且提交记录插入成功
- **THEN** 该题目的 submitNum 加 1，无论后续判题结果如何

#### Scenario: 并发提交不丢计数
- **WHEN** 多个用户同时向同一题目提交代码
- **THEN** submitNum 的增量等于成功创建的提交记录数（无丢失更新）

### Requirement: 通过计数

判题完成后，系统必须根据本次判题结果判定是否通过：判题信息为 Accepted（`JudgeInfoMessageEnum.ACCEPTED` 对应值）时，对该题目的 acceptedNum 执行原子自增；其他结果（答案错误、超时、内存溢出等）不得改变 acceptedNum。同一提交只计一次通过。

#### Scenario: 判题通过计数
- **WHEN** 某提交判题结果为 Accepted
- **THEN** 该题目的 acceptedNum 加 1

#### Scenario: 判题未通过不计数
- **WHEN** 某提交判题结果为 Wrong Answer、超时或其他非 Accepted 消息
- **THEN** 该题目的 acceptedNum 保持不变

### Requirement: 判题失败状态

判题流程发生可重试异常（如代码沙箱瞬时不可用、调用超时）时，系统必须按有限次数的退避重试，重试耗尽后才将该提交的判题状态置为 FAILED（status=3）；发生非可重试异常（如提交对应的题目不存在）时必须立即置为 FAILED。置为 FAILED 时必须记录异常日志。任何情况下不得使提交永久停留在"等待中"（status=0）或"判题中"（status=1）。判题权的获得必须以"仅当状态为等待中时置为判题中"的条件更新为唯一凭据，同一提交不得被并发重复判题，acceptedNum 不因判题重复触发而重复自增。

#### Scenario: 沙箱瞬时不可用重试后恢复
- **WHEN** 判题过程中调用代码沙箱发生瞬时失败，且在重试次数内恢复
- **THEN** 该提交正常完成判题，状态置为 2（成功），通过计数按结果正常处理

#### Scenario: 沙箱不可用时置失败
- **WHEN** 判题过程中调用代码沙箱持续抛出异常直至重试耗尽，或发生非可重试异常
- **THEN** 该提交的 status 更新为 3（失败），且异常被记录日志

#### Scenario: 重复判题请求被幂等跳过
- **WHEN** 同一提交的判题被重复触发（如消息重投递），且该提交已不在"等待中"状态
- **THEN** 重复的判题执行不产生任何状态或计数变更，acceptedNum 不重复自增

### Requirement: 计数器校准

系统必须提供每日定时校准任务：从提交流水全量重算每道未删除题目的提交数与通过数，与计数器比对，不一致时覆盖写回并记录漂移日志。统计口径：提交数为该题目全部提交记录数；通过数为已判题（status=2）且判题信息中 message 为 Accepted 的提交数；judgeInfo 缺失或无法解析的提交计入提交数、不计入通过数。校准任务首次运行必须完成存量历史数据的回填。

#### Scenario: 计数器漂移被收敛
- **WHEN** 某题目的 submitNum 或 acceptedNum 与提交流水统计不一致（如被手工改脏）
- **THEN** 校准任务运行后该题计数器恢复为与流水一致的值，并记录漂移日志

#### Scenario: 历史数据回填
- **WHEN** 库中存在历史提交记录而各题计数器为 0，校准任务首次运行
- **THEN** 各题目计数器被回填为按上述口径统计的真实值

#### Scenario: 脏判题信息的口径
- **WHEN** 某提交的 judgeInfo 为空或无法解析
- **THEN** 该提交计入所属题目的提交数，不计入通过数

### Requirement: 计数器无旁路覆写

题目的新增、更新、编辑接口不得提供修改 submitNum 与 acceptedNum 的途径；计数器的唯一写入方为提交/判题链路与校准任务。

#### Scenario: 更新题目不影响计数器
- **WHEN** 管理员或题目创建者通过更新或编辑接口修改题目内容
- **THEN** 该题目的 submitNum 与 acceptedNum 保持不变

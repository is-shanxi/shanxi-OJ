# 判题提交压测（rabbit-judge-async 验收）

## 运行

```bash
# 默认口径：200 线程 × 60 秒（rabbitmq 模式与 thread 模式各跑一轮对比）
D:\Jmeter\apache-jmeter-5.6.3\bin\jmeter.bat -n -t question-submit-load-test.jmx -l result.jtl

# 缩减冒烟（-J 覆盖参数）
D:\Jmeter\apache-jmeter-5.6.3\bin\jmeter.bat -n -t question-submit-load-test.jmx -Jthreads=10 -Jrampup=5 -Jduration=15 -l result.jtl
```

前置：后端已按目标模式启动（`judge.async.type = thread | rabbitmq`，见 `application.yml`）；压测账号 `jmeter_load` 需已注册（首次运行脚本会自动为每个线程注册亦可手工注册一次）。

## 验收核对（两模式各一轮后执行）

```sql
-- 1) 零丢失：全部提交行到达终态（2/3），无永久 WAITING(0)/RUNNING(1)
SELECT SUM(status IN (2,3)) AS terminal, SUM(status IN (0,1)) AS nonterminal FROM question_submit;

-- 2) 计数一致（可选）：submitNum ≈ 该题提交行数（每日校准任务亦会收敛）
SELECT id, submitNum, acceptedNum FROM question WHERE id = 2095311603023826947;
```

- 接口无 5xx：JMeter 汇总 `Err: 0 (0.00%)`（.jmx 内置响应码断言）。
- rabbitmq 模式队列排空：管理台 `http://localhost:15672` 中 `yuoj.judge.queue` messages 归零。
- 两种模式 API 吞吐应接近（发布即返回）；判题排空速率差异 = 消费并发差异（thread≈commonPool 并行度，rabbitmq=并发 2），属预期。

## 实测记录（2026-09-07，缩减口径 10 线程 × 15 秒）

| 模式 | 样本 | API 吞吐 | 错误 | 判题排空 |
|---|---|---|---|---|
| thread | 2327 | 154.4/s | 0 | 2317 条 ≈ 263s（≈8.8/s，commonPool 15 并行） |
| rabbitmq | 5312 | 353.2/s | 0 | 约 5300 条 + 重复放大 ≈ 1410s（2 消费者 ≈3/s 正常判，重复消息 CAS 秒跳） |

两轮均满足验收：接口 0 错误（无 5xx）、压测提交行全部到终态（零丢失）、`yuoj.judge.queue` 最终归零。

> **压测实测教训（已修复）**：恢复任务 WAITING 重发阈值最初为 5 分钟，rabbitmq 模式下 ~5300 条积压使大量提交 WAITING 超 5 分钟，恢复任务把"仍在排队"的提交误判为消息丢失批量重发（队列 4322→5773，排空时间翻倍）。CAS 幂等保证了数据零损伤，但阈值已调整为 30 分钟（远大于正常积压时长）。
> rabbitmq 模式 API 吞吐更高（发布即返回，无 commonPool 争抢）；判题排空速率受消费者并发限制，体现削峰形态。全量 200×60 口径请按上方命令执行后回填本表。

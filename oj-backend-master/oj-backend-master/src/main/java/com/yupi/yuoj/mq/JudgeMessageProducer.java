package com.yupi.yuoj.mq;

/**
 * 判题任务触发通道（策略：thread 线程池 / rabbitmq 消息队列，由 judge.async.type 装配决定）。
 * 消息契约仅含提交 id，判题所需数据由判题流程自行从数据库读取
 */
public interface JudgeMessageProducer {

    /**
     * 异步触发一次判题
     *
     * @param questionSubmitId 提交 id
     */
    void sendJudgeTask(long questionSubmitId);
}

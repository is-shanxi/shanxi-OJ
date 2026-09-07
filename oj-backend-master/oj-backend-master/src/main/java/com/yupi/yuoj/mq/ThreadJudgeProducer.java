package com.yupi.yuoj.mq;

import com.yupi.yuoj.judge.JudgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * 线程池判题触发通道（judge.async.type = thread 时装配，默认通道）：
 * 保持原有 CompletableFuture.runAsync（ForkJoinPool.commonPool）行为，
 * 判题语义与 rabbitmq 通道完全一致（终态保证收敛在 JudgeServiceImpl）
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "judge.async.type", havingValue = "thread", matchIfMissing = true)
public class ThreadJudgeProducer implements JudgeMessageProducer {

    @Resource
    @Lazy
    private JudgeService judgeService;

    @Override
    public void sendJudgeTask(long questionSubmitId) {
        CompletableFuture.runAsync(() -> {
            try {
                judgeService.doJudge(questionSubmitId);
            } catch (Exception e) {
                // runAsync 会吞掉异常栈，这里记录线程池通道的判题异常观测点
                log.error("线程池通道判题异常, questionSubmitId = {}", questionSubmitId, e);
            }
        });
    }
}

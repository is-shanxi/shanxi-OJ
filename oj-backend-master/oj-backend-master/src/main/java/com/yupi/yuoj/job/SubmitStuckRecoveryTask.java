package com.yupi.yuoj.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yupi.yuoj.model.entity.QuestionSubmit;
import com.yupi.yuoj.model.enums.QuestionSubmitStatusEnum;
import com.yupi.yuoj.mq.JudgeMessageProducer;
import com.yupi.yuoj.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * 卡死提交恢复定时任务（与判题触发通道无关，thread / rabbitmq 模式下均生效）：
 * - 等待中（status=0）超过阈值的提交：经当前配置的判题通道重新触发判题
 *   （覆盖消息发送失败、broker 丢消息等场景；重复触发由判题侧 CAS 认领幂等兜住）
 * - 判题中（status=1）超过阈值的提交：条件更新置为失败（仅 RUNNING 可置，
 *   不会覆盖已完成的判题结果）
 */
@Slf4j
@Component
public class SubmitStuckRecoveryTask {

    /** 等待中卡死阈值（分钟）：需远大于正常队列积压时长，否则会把"仍在排队的提交"误判为消息丢失而放大积压（压测实测教训） */
    private static final long WAITING_STUCK_MINUTES = 30;

    /** 判题中卡死阈值（分钟），需远大于单次判题的最长耗时（重试上限约 7s） */
    private static final long RUNNING_STUCK_MINUTES = 30;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeMessageProducer judgeMessageProducer;

    /**
     * 每 5 分钟执行一次
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    public void recoverStuckSubmits() {
        recoverStuckWaiting();
        recoverStuckRunning();
    }

    private void recoverStuckWaiting() {
        // 阈值用 MySQL 时钟（NOW()）计算：createTime 由数据库 DEFAULT CURRENT_TIMESTAMP 写入本地墙钟，
        // 而 JDBC 参数因 serverTimezone=UTC 会做时区转换，用 Java Date 比对会产生 8 小时错位
        List<QuestionSubmit> stuckList = questionSubmitService.list(new QueryWrapper<QuestionSubmit>()
                .eq("status", QuestionSubmitStatusEnum.WAITING.getValue())
                .apply("createTime < DATE_SUB(NOW(), INTERVAL {0} MINUTE)", WAITING_STUCK_MINUTES));
        if (CollectionUtils.isEmpty(stuckList)) {
            return;
        }
        log.warn("发现等待中卡死提交 {} 条，重新触发判题", stuckList.size());
        for (QuestionSubmit submit : stuckList) {
            log.warn("重新触发卡死提交判题, questionSubmitId = {}, createTime = {}",
                    submit.getId(), submit.getCreateTime());
            judgeMessageProducer.sendJudgeTask(submit.getId());
        }
    }

    private void recoverStuckRunning() {
        List<QuestionSubmit> stuckList = questionSubmitService.list(new QueryWrapper<QuestionSubmit>()
                .eq("status", QuestionSubmitStatusEnum.RUNNING.getValue())
                .apply("createTime < DATE_SUB(NOW(), INTERVAL {0} MINUTE)", RUNNING_STUCK_MINUTES));
        if (CollectionUtils.isEmpty(stuckList)) {
            return;
        }
        log.warn("发现判题中卡死提交 {} 条，置为失败", stuckList.size());
        for (QuestionSubmit submit : stuckList) {
            // CAS：仅当仍处于判题中时置为失败，不覆盖已完成的判题结果
            boolean updated = questionSubmitService.casUpdateStatus(submit.getId(),
                    QuestionSubmitStatusEnum.RUNNING.getValue(), QuestionSubmitStatusEnum.FAILED.getValue());
            log.warn("卡死判题中提交处理结果 updated = {}, questionSubmitId = {}, createTime = {}",
                    updated, submit.getId(), submit.getCreateTime());
        }
    }
}

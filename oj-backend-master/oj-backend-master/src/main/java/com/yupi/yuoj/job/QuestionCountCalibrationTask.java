package com.yupi.yuoj.job;

import cn.hutool.json.JSONUtil;
import com.yupi.yuoj.judge.codesandbox.model.JudgeInfo;
import com.yupi.yuoj.model.entity.Question;
import com.yupi.yuoj.model.entity.QuestionSubmit;
import com.yupi.yuoj.model.enums.JudgeInfoMessageEnum;
import com.yupi.yuoj.model.enums.QuestionSubmitStatusEnum;
import com.yupi.yuoj.service.QuestionService;
import com.yupi.yuoj.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目计数校准定时任务：每日凌晨全量重算每道题目的 submitNum / acceptedNum，
 * 与计数器比对，不一致则覆盖写回并记录漂移日志（首次运行即完成存量历史数据回填）。
 *
 * 统计口径（与 specs/backend/question-acceptance-rate 一致，按提交次数计、不去重按人）：
 * - 提交数 = 该题目全部提交记录数（judgeInfo 缺失 / 解析失败 / 判题失败的提交计入提交数）
 * - 通过数 = 已判题（status=2）且 judgeInfo.message 为 Accepted 的提交数
 */
@Slf4j
@Component
public class QuestionCountCalibrationTask {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    /**
     * 每天凌晨 4 点执行
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public void calibrateQuestionCount() {
        log.info("题目计数校准任务开始");
        // @TableLogic 自动过滤已删除记录，得到全部未删除题目与提交
        List<Question> questionList = questionService.list();
        List<QuestionSubmit> questionSubmitList = questionSubmitService.list();
        if (CollectionUtils.isEmpty(questionList)) {
            log.info("题目计数校准任务结束, 无题目需要校准");
            return;
        }
        Map<Long, List<QuestionSubmit>> questionIdSubmitListMap = questionSubmitList.stream()
                .filter(questionSubmit -> questionSubmit.getQuestionId() != null)
                .collect(Collectors.groupingBy(QuestionSubmit::getQuestionId));
        int calibratedCount = 0;
        for (Question question : questionList) {
            long questionId = question.getId();
            List<QuestionSubmit> submits = questionIdSubmitListMap
                    .getOrDefault(questionId, Collections.emptyList());
            long expectedSubmitNum = submits.size();
            long expectedAcceptedNum = submits.stream().filter(this::isAcceptedSubmit).count();
            boolean submitNumDrift = question.getSubmitNum() == null
                    || expectedSubmitNum != question.getSubmitNum();
            boolean acceptedNumDrift = question.getAcceptedNum() == null
                    || expectedAcceptedNum != question.getAcceptedNum();
            if (!submitNumDrift && !acceptedNumDrift) {
                continue;
            }
            calibratedCount++;
            log.info("题目计数漂移, questionId = {}, submitNum 期望值 = {}, 实际值 = {}, " +
                            "acceptedNum 期望值 = {}, 实际值 = {}",
                    questionId, expectedSubmitNum, question.getSubmitNum(),
                    expectedAcceptedNum, question.getAcceptedNum());
            Question updateQuestion = new Question();
            updateQuestion.setId(question.getId());
            updateQuestion.setSubmitNum((int) expectedSubmitNum);
            updateQuestion.setAcceptedNum((int) expectedAcceptedNum);
            questionService.updateById(updateQuestion);
        }
        log.info("题目计数校准任务结束, 漂移并写回的题目数 = {}", calibratedCount);
    }

    /**
     * 单条提交的通过判定：已判题（status=2）且判题信息 message 为 Accepted；
     * judgeInfo 为空 / 无法解析时计入提交数、不计入通过数
     */
    private boolean isAcceptedSubmit(QuestionSubmit questionSubmit) {
        if (!QuestionSubmitStatusEnum.SUCCEED.getValue().equals(questionSubmit.getStatus())) {
            return false;
        }
        String judgeInfoStr = questionSubmit.getJudgeInfo();
        if (judgeInfoStr == null || judgeInfoStr.isEmpty()) {
            return false;
        }
        try {
            JudgeInfo judgeInfo = JSONUtil.toBean(judgeInfoStr, JudgeInfo.class);
            return judgeInfo != null
                    && JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage());
        } catch (Exception e) {
            // judgeInfo 无法解析：不计通过数
            log.warn("judgeInfo 解析失败, questionSubmitId = {}, judgeInfo = {}",
                    questionSubmit.getId(), judgeInfoStr);
            return false;
        }
    }
}

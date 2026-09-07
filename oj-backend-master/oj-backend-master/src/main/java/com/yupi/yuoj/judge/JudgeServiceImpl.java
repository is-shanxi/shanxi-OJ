package com.yupi.yuoj.judge;

import cn.hutool.json.JSONUtil;
import com.yupi.yuoj.common.ErrorCode;
import com.yupi.yuoj.exception.BusinessException;
import com.yupi.yuoj.judge.codesandbox.CodeSandbox;
import com.yupi.yuoj.judge.codesandbox.CodeSandboxFactory;
import com.yupi.yuoj.judge.codesandbox.CodeSandboxProxy;
import com.yupi.yuoj.judge.codesandbox.model.ExecuteCodeRequest;
import com.yupi.yuoj.judge.codesandbox.model.ExecuteCodeResponse;
import com.yupi.yuoj.judge.strategy.JudgeContext;
import com.yupi.yuoj.model.dto.question.JudgeCase;
import com.yupi.yuoj.judge.codesandbox.model.JudgeInfo;
import com.yupi.yuoj.model.entity.Question;
import com.yupi.yuoj.model.entity.QuestionSubmit;
import com.yupi.yuoj.model.enums.JudgeInfoMessageEnum;
import com.yupi.yuoj.model.enums.QuestionSubmitStatusEnum;
import com.yupi.yuoj.service.QuestionService;
import com.yupi.yuoj.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeManager judgeManager;

    @Value("${codesandbox.type:example}")
    private String type;

    @Value("${codesandbox.http-connect-timeout:5000}")
    private int sandboxHttpConnectTimeout;

    @Value("${codesandbox.http-read-timeout:60000}")
    private int sandboxHttpReadTimeout;

    /**
     * 沙箱调用重试模板：首次 + 3 次重试，指数退避 1s/2s/4s（累计约 7s）。
     * 重试期间不触碰提交状态，判题终态保证单点收敛在 doJudge
     */
    private final RetryTemplate sandboxRetryTemplate = buildSandboxRetryTemplate();

    private RetryTemplate buildSandboxRetryTemplate() {
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMultiplier(2);
        backOffPolicy.setMaxInterval(4000);
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(new SimpleRetryPolicy(4));
        return retryTemplate;
    }

    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1）传入题目的提交 id，获取到对应的题目、提交信息（包含代码、编程语言等）
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        Long questionId = questionSubmit.getQuestionId();
        Question question = questionService.getById(questionId);
        if (question == null) {
            // 非可重试业务异常：立即置为失败，避免该提交被卡死恢复任务无限重发
            questionSubmitService.markJudgeFailed(questionSubmitId);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }
        // 2）CAS 认领判题权：仅当等待中（0）→ 判题中（1），
        //    更新 0 行说明已被其他执行流认领或已是终态，直接跳过（幂等，防重复判题）
        boolean claimed = questionSubmitService.casUpdateStatus(questionSubmitId,
                QuestionSubmitStatusEnum.WAITING.getValue(), QuestionSubmitStatusEnum.RUNNING.getValue());
        if (!claimed) {
            log.info("提交不在等待中，跳过重复判题, questionSubmitId = {}", questionSubmitId);
            return null;
        }
        // 3）调用沙箱，获取到执行结果（沙箱瞬时失败按指数退避重试，重试期间不置终态）
        JudgeInfo judgeInfo;
        try {
            String judgeCaseStr = question.getJudgeCase();
            List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
            List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
            ExecuteCodeResponse executeCodeResponse = sandboxRetryTemplate.execute(context -> {
                CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type, sandboxHttpConnectTimeout, sandboxHttpReadTimeout);
                codeSandbox = new CodeSandboxProxy(codeSandbox);
                String language = questionSubmit.getLanguage();
                String code = questionSubmit.getCode();
                ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                        .code(code)
                        .language(language)
                        .inputList(inputList)
                        .build();
                return codeSandbox.executeCode(executeCodeRequest);
            });
            // 4）根据沙箱的执行结果，设置题目的判题状态和信息
            JudgeContext judgeContext = new JudgeContext();
            judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
            judgeContext.setInputList(inputList);
            judgeContext.setOutputList(executeCodeResponse.getOutputList());
            judgeContext.setJudgeCaseList(judgeCaseList);
            judgeContext.setQuestion(question);
            judgeContext.setQuestionSubmit(questionSubmit);
            judgeInfo = judgeManager.doJudge(judgeContext);
        } catch (Exception e) {
            // 重试耗尽仍失败或判题策略异常：置失败终态后以业务异常上抛（消费端确认消息，线程池模式由生产者记录日志）
            log.error("判题异常, questionSubmitId = {}", questionSubmitId, e);
            questionSubmitService.markJudgeFailed(questionSubmitId);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题失败：" + e.getMessage());
        }
        // 5）修改数据库中的判题结果
        QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
        questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        boolean update = questionSubmitService.updateById(questionSubmitUpdate);
        if (!update) {
            // 提交行可能已被删除：此时状态尚未到终态，不能以 BusinessException 误导消费端按
            // "业务已置终态"确认消息；抛非 BusinessException 让消费端 nack 转死信队列人工处置
            throw new IllegalStateException("判题结果落库失败, questionSubmitId = " + questionSubmitId);
        }
        // 6）判题通过则通过数 +1（判据统一引用枚举常量；提交数已在提交时计入，此处不再自增 submitNum）
        if (JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage())) {
            questionService.incrementAcceptedNum(questionId);
        }
        return questionSubmitService.getById(questionSubmitId);
    }
}

package com.yupi.yuoj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yuoj.model.dto.question.QuestionQueryRequest;
import com.yupi.yuoj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.yupi.yuoj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.yupi.yuoj.model.entity.Question;
import com.yupi.yuoj.model.entity.QuestionSubmit;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yuoj.model.entity.User;
import com.yupi.yuoj.model.vo.QuestionSubmitVO;
import com.yupi.yuoj.model.vo.QuestionVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author 李鱼皮
* @description 针对表【question_submit(题目提交)】的数据库操作Service
* @createDate 2023-08-07 20:58:53
*/
public interface QuestionSubmitService extends IService<QuestionSubmit> {
    
    /**
     * 题目提交
     *
     * @param questionSubmitAddRequest 题目提交信息
     * @param loginUser
     * @return
     */
    long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser);

    /**
     * 获取查询条件
     *
     * @param questionSubmitQueryRequest
     * @return
     */
    QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitQueryRequest);

    /**
     * 获取题目封装
     *
     * @param questionSubmit
     * @param loginUser
     * @return
     */
    QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit, User loginUser);

    /**
     * 分页获取题目封装
     *
     * @param questionSubmitPage
     * @param loginUser
     * @return
     */
    Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser);

    /**
     * 条件更新提交状态（CAS）：仅当当前状态为 fromStatus 时置为 toStatus，
     * 返回是否更新成功（0 行视为未认领/已终态），用于判题幂等认领与僵尸状态回收
     *
     * @param questionSubmitId 提交 id
     * @param fromStatus       期望的当前状态
     * @param toStatus         目标状态
     * @return 是否更新成功
     */
    boolean casUpdateStatus(long questionSubmitId, int fromStatus, int toStatus);

    /**
     * 将非终态（等待中/判题中）的提交置为判题失败（status=3），
     * 已是终态（成功/失败）的提交不受影响
     *
     * @param questionSubmitId 提交 id
     * @return 是否更新成功
     */
    boolean markJudgeFailed(long questionSubmitId);
}

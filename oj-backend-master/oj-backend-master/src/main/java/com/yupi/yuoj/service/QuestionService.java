package com.yupi.yuoj.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yupi.yuoj.model.dto.question.QuestionQueryRequest;
import com.yupi.yuoj.model.entity.Question;
import com.yupi.yuoj.model.entity.Question;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yuoj.model.vo.QuestionVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author 李鱼皮
* @description 针对表【question(题目)】的数据库操作Service
* @createDate 2023-08-07 20:58:00
*/
public interface QuestionService extends IService<Question> {


    /**
     * 校验
     *
     * @param question
     * @param add
     */
    void validQuestion(Question question, boolean add);

    /**
     * 获取查询条件
     *
     * @param questionQueryRequest
     * @return
     */
    QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest);
    
    /**
     * 获取题目封装
     *
     * @param question
     * @param request
     * @return
     */
    QuestionVO getQuestionVO(Question question, HttpServletRequest request);

    /**
     * 分页获取题目封装
     *
     * @param questionPage
     * @param request
     * @return
     */
    Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request);

    /**
     * 题目提交数原子自增（+1），数据库端单语句自增，保证并发下无丢失更新
     *
     * @param questionId 题目 id
     * @return 是否更新成功
     */
    boolean incrementSubmitNum(long questionId);

    /**
     * 题目通过数原子自增（+1），数据库端单语句自增，保证并发下无丢失更新
     *
     * @param questionId 题目 id
     * @return 是否更新成功
     */
    boolean incrementAcceptedNum(long questionId);

}

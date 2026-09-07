package com.yupi.yuoj.mq;

import com.yupi.yuoj.exception.BusinessException;
import com.yupi.yuoj.judge.JudgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import javax.annotation.Resource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 判题消息消费者：手动 ack 模式。
 * 判题正常完成或判题流程已将提交置为终态（BusinessException）→ 确认消息；
 * 与判题业务无关的意外异常 → nack(requeue=false) 转入死信队列，避免毒丸消息无限重投
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "judge.async.type", havingValue = "rabbitmq")
public class JudgeMessageConsumer {

    @Resource
    @Lazy
    private JudgeService judgeService;

    @RabbitListener(queues = RabbitMQConfig.JUDGE_QUEUE)
    public void onJudgeTask(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        long questionSubmitId;
        try {
            questionSubmitId = Long.parseLong(body.trim());
        } catch (NumberFormatException e) {
            // 消息内容非法（毒丸）：不重新入队，转入死信队列人工处置
            log.error("判题消息内容非法, body = {}", body, e);
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        try {
            judgeService.doJudge(questionSubmitId);
            // 判题正常完成（含提交已被其他执行流认领而跳过的情况），确认消息
            channel.basicAck(deliveryTag, false);
        } catch (BusinessException e) {
            // 判题流程已将提交置为失败终态（业务已处理），确认消息避免重投
            log.error("判题业务异常（提交已置终态）, questionSubmitId = {}, code = {}, message = {}",
                    questionSubmitId, e.getCode(), e.getMessage());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 与判题业务无关的意外异常：不重新入队，转入死信队列（提交由卡死恢复任务兜底）
            log.error("判题消息处理发生意外异常, questionSubmitId = {}", questionSubmitId, e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}

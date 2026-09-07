package com.yupi.yuoj.mq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * RabbitMQ 判题触发通道（judge.async.type = rabbitmq 时装配）：
 * 发送持久化消息（内容为提交 id），publisher confirm / return 结果记录日志；
 * 发送失败不向用户报错——提交保持等待中，由卡死恢复任务兜底重发
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "judge.async.type", havingValue = "rabbitmq")
public class RabbitJudgeProducer implements JudgeMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 注册投递确认回调：confirm 为 broker 是否收到，return 为消息是否成功路由到队列
     */
    @PostConstruct
    public void initConfirmCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            String id = correlationData == null ? "unknown" : correlationData.getId();
            if (ack) {
                log.info("判题消息投递确认成功, questionSubmitId = {}", id);
            } else {
                log.error("判题消息投递被 broker 拒绝(nack), questionSubmitId = {}, cause = {}", id, cause);
            }
        });
        rabbitTemplate.setReturnsCallback(returned ->
                log.error("判题消息无法路由到队列, exchange = {}, routingKey = {}, replyText = {}, message = {}",
                        returned.getExchange(), returned.getRoutingKey(), returned.getReplyText(),
                        new String(returned.getMessage().getBody(), StandardCharsets.UTF_8)));
    }

    @Override
    public void sendJudgeTask(long questionSubmitId) {
        String idStr = String.valueOf(questionSubmitId);
        try {
            MessageProperties properties = MessagePropertiesBuilder.newInstance()
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .setCorrelationId(idStr)
                    .build();
            Message message = MessageBuilder.withBody(idStr.getBytes(StandardCharsets.UTF_8))
                    .andProperties(properties)
                    .build();
            rabbitTemplate.convertAndSend(RabbitMQConfig.JUDGE_EXCHANGE, RabbitMQConfig.JUDGE_ROUTING_KEY,
                    message, new CorrelationData(idStr));
            log.info("判题消息已发送, questionSubmitId = {}", questionSubmitId);
        } catch (AmqpException e) {
            // 发布失败不向用户报错：提交保持等待中，由卡死恢复任务兜底重发（观测点）
            log.error("判题消息发送失败, questionSubmitId = {}", questionSubmitId, e);
        }
    }
}

package com.yupi.yuoj.mq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 判题拓扑声明：direct 交换机 + 判题队列（durable，带死信参数）+ 死信交换机/队列。
 * 仅在 judge.async.type = rabbitmq 时装配，thread 模式下不创建任何 MQ Bean
 */
@Configuration
@ConditionalOnProperty(name = "judge.async.type", havingValue = "rabbitmq")
public class RabbitMQConfig {

    /** 判题交换机 */
    public static final String JUDGE_EXCHANGE = "yuoj.judge.exchange";

    /** 判题队列 */
    public static final String JUDGE_QUEUE = "yuoj.judge.queue";

    /** 判题路由键 */
    public static final String JUDGE_ROUTING_KEY = "judge.submit";

    /** 死信交换机 */
    public static final String JUDGE_DLX = "yuoj.judge.dlx";

    /** 死信队列 */
    public static final String JUDGE_DLQ = "yuoj.judge.dlq";

    /** 死信路由键 */
    public static final String JUDGE_DLQ_ROUTING_KEY = "judge.dead";

    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(JUDGE_EXCHANGE, true, false);
    }

    @Bean
    public Queue judgeQueue() {
        // durable 队列 + 死信参数：消费端 nack(requeue=false) 的消息转入死信队列人工处置
        return QueueBuilder.durable(JUDGE_QUEUE)
                .deadLetterExchange(JUDGE_DLX)
                .deadLetterRoutingKey(JUDGE_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding judgeBinding() {
        return BindingBuilder.bind(judgeQueue()).to(judgeExchange()).with(JUDGE_ROUTING_KEY);
    }

    @Bean
    public DirectExchange judgeDeadLetterExchange() {
        return new DirectExchange(JUDGE_DLX, true, false);
    }

    @Bean
    public Queue judgeDeadLetterQueue() {
        return QueueBuilder.durable(JUDGE_DLQ).build();
    }

    @Bean
    public Binding judgeDeadLetterBinding() {
        return BindingBuilder.bind(judgeDeadLetterQueue()).to(judgeDeadLetterExchange()).with(JUDGE_DLQ_ROUTING_KEY);
    }
}

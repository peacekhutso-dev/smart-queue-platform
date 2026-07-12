package com.dha.branch.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_EXCHANGE = "queue.events";
    public static final String QUEUE_STATS_QUEUE = "branch-service.queue-events";

    @Bean
    public TopicExchange queueEventsExchange() {
        return new TopicExchange(QUEUE_EXCHANGE, true, false);
    }

    @Bean
    public Queue queueStatsQueue() {
        return new Queue(QUEUE_STATS_QUEUE, true);
    }

    @Bean
    public Binding queueStatsBinding(Queue queueStatsQueue, TopicExchange queueEventsExchange) {
        return BindingBuilder.bind(queueStatsQueue).to(queueEventsExchange).with("queue.#");
    }
}

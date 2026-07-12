package com.dha.queue.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_EXCHANGE = "queue.events";

    @Bean
    public TopicExchange queueEventsExchange() {
        return new TopicExchange(QUEUE_EXCHANGE, true, false);
    }
}

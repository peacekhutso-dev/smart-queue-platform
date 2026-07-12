package com.dha.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String PAYMENT_QUEUE = "notification-gateway.payment-events";

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentNotificationQueue() {
        return new Queue(PAYMENT_QUEUE, true);
    }

    @Bean
    public Binding paymentNotificationBinding(Queue paymentNotificationQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentNotificationQueue).to(paymentEventsExchange).with("payment.#");
    }
}

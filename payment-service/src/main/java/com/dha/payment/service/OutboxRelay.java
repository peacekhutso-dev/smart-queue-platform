package com.dha.payment.service;

import com.dha.payment.config.RabbitMQConfig;
import com.dha.payment.entity.OutboxMessage;
import com.dha.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Polls the outbox table and publishes anything not yet sent. Decouples "the
 * DB write succeeded" from "RabbitMQ was reachable at that instant" — a
 * payment.succeeded row is never lost just because the broker hiccuped
 * mid-request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        List<OutboxMessage> pending = outboxRepository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxMessage message : pending) {
            try {
                rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EXCHANGE, message.getRoutingKey(), message.getPayload());
                message.setPublishedAt(Instant.now());
                outboxRepository.save(message);
            } catch (Exception e) {
                log.warn("Failed to relay outbox message {} (routingKey={}), will retry next tick",
                        message.getId(), message.getRoutingKey(), e);
            }
        }
    }
}

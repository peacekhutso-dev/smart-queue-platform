package com.dha.notification.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import static com.dha.notification.config.RabbitMQConfig.PAYMENT_QUEUE;

/**
 * Relays payment.events off RabbitMQ onto the browser's STOMP topic for that
 * user. Payload shape matches what PaymentService.writeOutbox publishes:
 * {paymentId, userId, queueEntryId, amount, currency, status, occurredAt}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = PAYMENT_QUEUE)
    public void onPaymentEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String userId = node.path("userId").asText();
            if (userId.isBlank()) {
                log.warn("Payment event missing userId, dropping: {}", payload);
                return;
            }
            messagingTemplate.convertAndSend("/topic/user/" + userId + "/payment-status", payload);
        } catch (Exception e) {
            log.error("Failed to relay payment event: {}", payload, e);
        }
    }
}

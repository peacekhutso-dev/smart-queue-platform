package com.dha.payment.service;

import com.dha.payment.dto.request.CreatePaymentIntentRequest;
import com.dha.payment.entity.OutboxMessage;
import com.dha.payment.entity.Payment;
import com.dha.payment.entity.PaymentEvent;
import com.dha.payment.entity.PaymentStatus;
import com.dha.payment.provider.CheckoutSession;
import com.dha.payment.provider.PaymentProviderGateway;
import com.dha.payment.provider.WebhookEvent;
import com.dha.payment.repository.OutboxRepository;
import com.dha.payment.repository.PaymentEventRepository;
import com.dha.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentProviderGateway providerGateway;
    private final ObjectMapper objectMapper;

    @Transactional
    public CheckoutResult createIntent(UUID userId, CreatePaymentIntentRequest request) {
        var existing = paymentRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.getUserId().equals(userId)) {
                throw new SecurityException("Idempotency key belongs to a different user");
            }
            CheckoutSession session = providerGateway.createCheckoutSession(payment);
            return new CheckoutResult(payment, session.redirectUrl());
        }

        Payment payment = Payment.builder()
                .userId(userId)
                .queueEntryId(request.queueEntryId())
                .amount(request.amount())
                .currency(request.currency().toUpperCase())
                .status(PaymentStatus.CREATED)
                .provider(providerGateway.name())
                .idempotencyKey(request.idempotencyKey())
                .build();

        CheckoutSession session = providerGateway.createCheckoutSession(payment);
        payment.setProviderReference(session.providerReference());
        payment.setStatus(PaymentStatus.PENDING);
        payment = paymentRepository.save(payment);

        writeOutbox("payment.created", payment);
        log.info("Created payment intent {} for userId={} amount={} {}",
                payment.getId(), payment.getUserId(), payment.getAmount(), payment.getCurrency());

        return new CheckoutResult(payment, session.redirectUrl());
    }

    public Payment getPayment(UUID id, UUID requesterId, String requesterRole) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Payment not found: " + id));

        boolean isOwner = payment.getUserId().equals(requesterId);
        boolean isStaff = "STAFF".equals(requesterRole) || "ADMIN".equals(requesterRole);
        if (!isOwner && !isStaff) {
            throw new SecurityException("You do not have access to this payment");
        }
        return payment;
    }

    /**
     * Verify -> dedupe by providerEventId -> update -> outbox, all in one
     * transaction. Must stay fast: no downstream synchronous calls here.
     */
    @Transactional
    public void handleWebhook(String rawPayload, String signatureHeader) {
        if (!providerGateway.verifyWebhookSignature(rawPayload, signatureHeader)) {
            throw new SecurityException("Invalid webhook signature");
        }

        WebhookEvent event = providerGateway.parseWebhookEvent(rawPayload);

        if (paymentEventRepository.existsByProviderEventId(event.providerEventId())) {
            log.info("Ignoring duplicate webhook delivery for providerEventId={}", event.providerEventId());
            return;
        }

        Payment payment = paymentRepository.findByProviderReference(event.providerReference())
                .orElseThrow(() -> new NoSuchElementException(
                        "No payment for providerReference=" + event.providerReference()));

        payment.setStatus(event.status());
        paymentRepository.save(payment);

        paymentEventRepository.save(PaymentEvent.builder()
                .paymentId(payment.getId())
                .status(event.status())
                .providerEventId(event.providerEventId())
                .payload(rawPayload)
                .build());

        String routingKey = "payment." + event.status().name().toLowerCase();
        writeOutbox(routingKey, payment);

        log.info("Payment {} transitioned to {} via webhook", payment.getId(), event.status());
    }

    private void writeOutbox(String routingKey, Payment payment) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("paymentId", payment.getId().toString());
            body.put("userId", payment.getUserId());
            body.put("queueEntryId", payment.getQueueEntryId());
            body.put("amount", payment.getAmount());
            body.put("currency", payment.getCurrency());
            body.put("status", payment.getStatus().name());
            body.put("occurredAt", payment.getUpdatedAt().toString());
            String json = objectMapper.writeValueAsString(body);
            outboxRepository.save(OutboxMessage.builder()
                    .routingKey(routingKey)
                    .payload(json)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }

    public record CheckoutResult(Payment payment, String redirectUrl) {
    }
}

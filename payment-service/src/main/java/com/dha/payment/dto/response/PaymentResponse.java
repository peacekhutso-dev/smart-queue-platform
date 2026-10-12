package com.dha.payment.dto.response;

import com.dha.payment.entity.Payment;
import com.dha.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID userId,
        Long queueEntryId,
        BigDecimal amount,
        String currency,
        PaymentStatus status,
        String provider,
        String redirectUrl,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaymentResponse of(Payment payment) {
        return of(payment, null);
    }

    public static PaymentResponse of(Payment payment, String redirectUrl) {
        return new PaymentResponse(
                payment.getId(),
                payment.getUserId(),
                payment.getQueueEntryId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getProvider(),
                redirectUrl,
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}

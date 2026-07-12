package com.dha.payment.provider;

import com.dha.payment.entity.PaymentStatus;

public record WebhookEvent(
        String providerEventId,
        String providerReference,
        PaymentStatus status,
        String rawPayload
) {
}

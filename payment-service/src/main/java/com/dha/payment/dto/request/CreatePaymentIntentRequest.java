package com.dha.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * userId is deliberately absent — it's taken from the Gateway-verified
 * principal (PaymentController), never from the client, so a caller can't
 * create a payment intent attributed to someone else. idempotencyKey is
 * required so a retried request can't create a duplicate charge.
 */
public record CreatePaymentIntentRequest(
        Long queueEntryId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String idempotencyKey
) {
}

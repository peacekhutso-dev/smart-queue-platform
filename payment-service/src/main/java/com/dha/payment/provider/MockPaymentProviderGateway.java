package com.dha.payment.provider;

import com.dha.payment.entity.Payment;
import com.dha.payment.entity.PaymentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default provider while no real payment processor has been chosen yet
 * (see migration plan — "provider-agnostic for now"). Simulates a hosted
 * checkout: redirectUrl points at a local mock page, "webhooks" are just
 * whatever JSON the mock page posts back. Swap this out for a real
 * StripePaymentProviderGateway/PayFastPaymentProviderGateway later without
 * touching PaymentService or the controller.
 */
@Component
@RequiredArgsConstructor
public class MockPaymentProviderGateway implements PaymentProviderGateway {

    private final ObjectMapper objectMapper;

    @Value("${app.mock-provider.checkout-base-url:http://localhost:5173/mock-checkout}")
    private String checkoutBaseUrl;

    @Value("${app.mock-provider.webhook-secret:mock-webhook-secret}")
    private String webhookSecret;

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public CheckoutSession createCheckoutSession(Payment payment) {
        String providerReference = "mock_sess_" + UUID.randomUUID();
        String redirectUrl = checkoutBaseUrl + "?session=" + providerReference
                + "&amount=" + payment.getAmount() + "&currency=" + payment.getCurrency();
        return new CheckoutSession(providerReference, redirectUrl);
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
        return webhookSecret.equals(signatureHeader);
    }

    @Override
    public WebhookEvent parseWebhookEvent(String rawPayload) {
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            String providerEventId = node.path("eventId").asText(UUID.randomUUID().toString());
            String providerReference = node.path("session").asText();
            String statusText = node.path("status").asText("SUCCEEDED");
            PaymentStatus status = PaymentStatus.valueOf(statusText.toUpperCase());
            return new WebhookEvent(providerEventId, providerReference, status, rawPayload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed mock webhook payload", e);
        }
    }
}

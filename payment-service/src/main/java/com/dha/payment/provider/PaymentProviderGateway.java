package com.dha.payment.provider;

import com.dha.payment.entity.Payment;

/**
 * The one seam a real provider (Stripe, PayFast, ...) plugs into. Keeps the
 * REST/event contract (PaymentController, payment.events) fixed regardless
 * of which provider gets picked later — swap the @Primary bean, nothing else
 * in the service changes.
 */
public interface PaymentProviderGateway {

    String name();

    CheckoutSession createCheckoutSession(Payment payment);

    boolean verifyWebhookSignature(String rawPayload, String signatureHeader);

    WebhookEvent parseWebhookEvent(String rawPayload);
}

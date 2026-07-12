package com.dha.payment.provider;

/**
 * What a provider hands back when we ask it to start a hosted checkout.
 * redirectUrl points at the PROVIDER's own domain — the browser goes there
 * directly, our frontend never renders a card field.
 */
public record CheckoutSession(String providerReference, String redirectUrl) {
}

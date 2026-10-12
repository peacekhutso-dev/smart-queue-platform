package com.dha.payment.security;

import java.util.UUID;

/**
 * The authenticated caller, built directly from the Gateway-forwarded
 * identity headers — no DB lookup, no JWT parsing here.
 */
public record JwtPrincipal(UUID userId, String email, String role) {
}

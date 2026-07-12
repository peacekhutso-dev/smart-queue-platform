package com.dha.catalog.security;

import java.util.UUID;

/**
 * The authenticated caller, built directly from a verified JWT's claims —
 * no DB lookup. Mirrors the same record used across the other services;
 * this service never checks a password itself.
 */
public record JwtPrincipal(UUID userId, String email, String role) {
}

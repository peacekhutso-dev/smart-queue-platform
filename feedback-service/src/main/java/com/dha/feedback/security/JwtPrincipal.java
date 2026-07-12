package com.dha.feedback.security;

import java.util.UUID;

/**
 * The authenticated caller, built directly from a verified JWT's claims —
 * no DB lookup. Replaces the old pattern of loading a Spring Security
 * UserDetails from a local `users` table, which no longer exists here now
 * that identity-service owns it.
 */
public record JwtPrincipal(UUID userId, String email, String role) {
}

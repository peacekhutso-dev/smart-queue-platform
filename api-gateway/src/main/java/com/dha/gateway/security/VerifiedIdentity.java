package com.dha.gateway.security;

import java.util.UUID;

public record VerifiedIdentity(UUID userId, String email, String role) {
}

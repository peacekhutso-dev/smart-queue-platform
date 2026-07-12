package com.dha.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtVerifier {

    private final JwksProvider jwksProvider;

    public Mono<VerifiedIdentity> verify(String token) {
        return jwksProvider.getPublicKey()
                .flatMap(key -> parse(token, key))
                .onErrorResume(JwtException.class, e -> {
                    log.debug("JWT verification failed with cached key, refreshing JWKS and retrying once");
                    return jwksProvider.refresh().flatMap(key -> parse(token, key));
                });
    }

    private Mono<VerifiedIdentity> parse(String token, RSAPublicKey key) {
        return Mono.fromCallable(() -> {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
            UUID userId = UUID.fromString(claims.get("userId", String.class));
            String role = claims.get("role", String.class);
            return new VerifiedIdentity(userId, claims.getSubject(), role);
        });
    }
}

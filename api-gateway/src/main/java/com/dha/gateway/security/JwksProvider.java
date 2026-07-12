package com.dha.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reactive counterpart to the monolith's JwksProvider — fetches and caches
 * identity-service's RSA public key so the Gateway can verify every request's
 * JWT once, centrally, instead of every downstream service doing it
 * independently.
 */
@Component
@Slf4j
public class JwksProvider {

    @Value("${app.identity-service.url:http://localhost:8085}")
    private String identityServiceUrl;

    private final WebClient webClient = WebClient.builder().build();
    private final AtomicReference<RSAPublicKey> cached = new AtomicReference<>();

    public Mono<RSAPublicKey> getPublicKey() {
        RSAPublicKey key = cached.get();
        return key != null ? Mono.just(key) : refresh();
    }

    public Mono<RSAPublicKey> refresh() {
        return webClient.get()
                .uri(identityServiceUrl + "/.well-known/jwks.json")
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseKey)
                .doOnNext(key -> {
                    cached.set(key);
                    log.info("Refreshed identity-service JWKS public key");
                });
    }

    private RSAPublicKey parseKey(String json) {
        try {
            JsonNode keyNode = new ObjectMapper().readTree(json).path("keys").get(0);
            BigInteger modulus = decode(keyNode.path("n").asText());
            BigInteger exponent = decode(keyNode.path("e").asText());
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulus, exponent));
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse JWKS from identity-service", e);
        }
    }

    private BigInteger decode(String base64Url) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(base64Url));
    }
}

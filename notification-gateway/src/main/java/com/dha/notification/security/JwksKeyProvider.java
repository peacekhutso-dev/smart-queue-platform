package com.dha.notification.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches and caches identity-service's RSA public key so this service can
 * verify a JWT itself for STOMP CONNECT frames — the one path the Gateway
 * can't verify (the JWT lives inside the STOMP frame body, not an HTTP
 * header the Gateway's ZeroTrustGlobalFilter can inspect).
 */
@Component
@Slf4j
public class JwksKeyProvider {

    @Value("${app.identity-service.url:http://localhost:8085}")
    private String identityServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AtomicReference<RSAPublicKey> cached = new AtomicReference<>();

    public RSAPublicKey getPublicKey() {
        RSAPublicKey key = cached.get();
        return key != null ? key : refresh();
    }

    public RSAPublicKey refresh() {
        String json = restTemplate.getForObject(identityServiceUrl + "/.well-known/jwks.json", String.class);
        RSAPublicKey key = parseKey(json);
        cached.set(key);
        log.info("Refreshed identity-service JWKS public key");
        return key;
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

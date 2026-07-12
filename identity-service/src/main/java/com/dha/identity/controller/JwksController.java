package com.dha.identity.controller;

import com.dha.identity.security.RsaKeyProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Publishes Identity's RSA public key so every other service can verify
 * JWT signatures locally (no shared secret, no synchronous call back to
 * Identity per request).
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyProvider keyProvider;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        RSAPublicKey publicKey = keyProvider.publicKey();
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", keyProvider.getKeyId(),
                "n", encode(publicKey.getModulus()),
                "e", encode(publicKey.getPublicExponent())
        )));
    }

    private String encode(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.dha.identity.security;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Signing key for JWT issuance. Generates a fresh RSA-2048 keypair on every
 * startup — simplest thing that lets Identity be the only token issuer and
 * everyone else verify via JWKS with no shared secret. Known limitation:
 * restarting this service invalidates all outstanding tokens (every client
 * just re-logs in). Pin the key to a mounted PEM file before this goes near
 * a real deployment.
 */
@Component
@Getter
@Slf4j
public class RsaKeyProvider {

    private final KeyPair keyPair;
    private final String keyId = UUID.randomUUID().toString();

    public RsaKeyProvider() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
            log.info("Generated RSA signing keypair, kid={}", keyId);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    public RSAPrivateKey privateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    public RSAPublicKey publicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }
}

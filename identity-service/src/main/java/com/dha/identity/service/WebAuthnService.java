package com.dha.identity.service;

import com.dha.identity.entity.User;
import com.dha.identity.entity.WebAuthnCredential;
import com.dha.identity.repository.UserRepository;
import com.dha.identity.repository.WebAuthnCredentialRepository;
import com.dha.identity.security.WebAuthnCredentialRepositoryAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yubico.webauthn.*;
import com.yubico.webauthn.data.*;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * STAFF/ADMIN fingerprint Zero Trust gate — WebAuthn platform authenticator (Touch ID /
 * Windows Hello / Android fingerprint) registration and authentication ceremonies.
 *
 * Mirrors the citizen face-verification flow: a successful authentication sets
 * fingerprint:{userId} in Redis, checked by the monolith's ZeroTrustFilter Gate 3.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebAuthnService {

    private static final String REG_CHALLENGE_PREFIX = "webauthn:reg:";
    private static final String AUTH_CHALLENGE_PREFIX = "webauthn:auth:";
    public static final String FINGERPRINT_PREFIX = "fingerprint:";

    private final RelyingParty relyingParty;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository webAuthnCredentialRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.zero-trust.fingerprint-ttl-hours:4}")
    private int fingerprintTtlHours;

    public String startRegistration(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserIdentity identity = UserIdentity.builder()
                .name(user.getEmail())
                .displayName(user.getFirstName() + " " + user.getLastName())
                .id(WebAuthnCredentialRepositoryAdapter.userHandleForId(user.getId()))
                .build();

        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(
                StartRegistrationOptions.builder().user(identity).build());

        storeChallenge(REG_CHALLENGE_PREFIX + user.getId(), toJson(options));

        try {
            return unwrapPublicKey(options.toCredentialsCreateJson());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise WebAuthn registration options", e);
        }
    }

    @Transactional
    public void finishRegistration(String userEmail, String credentialJson) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String storedJson = takeChallenge(REG_CHALLENGE_PREFIX + user.getId());
        if (storedJson == null) {
            throw new IllegalStateException("No pending registration for this user — request options again");
        }

        try {
            PublicKeyCredentialCreationOptions request = PublicKeyCredentialCreationOptions.fromJson(storedJson);
            PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs> pkc =
                    PublicKeyCredential.parseRegistrationResponseJson(credentialJson);

            RegistrationResult result = relyingParty.finishRegistration(
                    FinishRegistrationOptions.builder()
                            .request(request)
                            .response(pkc)
                            .build());

            WebAuthnCredential credential = WebAuthnCredential.builder()
                    .userId(user.getId())
                    .credentialId(result.getKeyId().getId().getBase64Url())
                    .publicKeyCose(result.getPublicKeyCose().getBase64Url())
                    .signatureCount(result.getSignatureCount())
                    .build();
            webAuthnCredentialRepository.save(credential);

            log.info("WebAuthn credential registered for userId={}", user.getId());
        } catch (IOException | RegistrationFailedException e) {
            throw new SecurityException("Fingerprint registration failed: " + e.getMessage());
        }
    }

    public String startAuthentication(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!webAuthnCredentialRepository.existsByUserId(user.getId())) {
            throw new IllegalStateException("NO_ENROLLMENT");
        }

        AssertionRequest request = relyingParty.startAssertion(
                StartAssertionOptions.builder().username(user.getEmail()).build());

        storeChallenge(AUTH_CHALLENGE_PREFIX + user.getId(), toJson(request));

        try {
            return unwrapPublicKey(request.toCredentialsGetJson());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise WebAuthn assertion options", e);
        }
    }

    @Transactional
    public void finishAuthentication(String userEmail, String credentialJson) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String storedJson = takeChallenge(AUTH_CHALLENGE_PREFIX + user.getId());
        if (storedJson == null) {
            throw new IllegalStateException("No pending fingerprint challenge for this user — request options again");
        }

        try {
            AssertionRequest request = AssertionRequest.fromJson(storedJson);
            PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs> pkc =
                    PublicKeyCredential.parseAssertionResponseJson(credentialJson);

            AssertionResult result = relyingParty.finishAssertion(
                    FinishAssertionOptions.builder()
                            .request(request)
                            .response(pkc)
                            .build());

            webAuthnCredentialRepository.findByCredentialId(result.getCredentialId().getBase64Url())
                    .ifPresent(cred -> {
                        cred.setSignatureCount(result.getSignatureCount());
                        webAuthnCredentialRepository.save(cred);
                    });

            String key = FINGERPRINT_PREFIX + user.getId();
            redisTemplate.opsForValue().set(key, "verified", fingerprintTtlHours, TimeUnit.HOURS);
            log.info("Fingerprint session granted for userId={} TTL={}h", user.getId(), fingerprintTtlHours);
        } catch (IOException | AssertionFailedException e) {
            throw new SecurityException("Fingerprint verification failed: " + e.getMessage());
        }
    }

    private void storeChallenge(String key, String json) {
        redisTemplate.opsForValue().set(key, json, 5, TimeUnit.MINUTES);
    }

    private String takeChallenge(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        return value != null ? value.toString() : null;
    }

    private String unwrapPublicKey(String wrappedJson) throws IOException {
        JsonNode wrapped = objectMapper.readTree(wrappedJson);
        return objectMapper.writeValueAsString(wrapped.get("publicKey"));
    }

    private String toJson(PublicKeyCredentialCreationOptions options) {
        try {
            return options.toJson();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise WebAuthn registration options", e);
        }
    }

    private String toJson(AssertionRequest request) {
        try {
            return request.toJson();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise WebAuthn assertion options", e);
        }
    }
}

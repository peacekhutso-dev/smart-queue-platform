package com.dha.identity.config;

import com.dha.identity.security.WebAuthnCredentialRepositoryAdapter;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Wires up the WebAuthn RelyingParty used for the STAFF/ADMIN fingerprint Zero Trust gate
 * (Touch ID / Windows Hello / Android fingerprint via the browser's platform authenticator).
 */
@Configuration
@RequiredArgsConstructor
public class WebAuthnConfig {

    private final WebAuthnCredentialRepositoryAdapter credentialRepository;

    @Value("${app.webauthn.rp-id}")
    private String rpId;

    @Value("${app.webauthn.rp-name}")
    private String rpName;

    @Value("${app.webauthn.origins}")
    private String originsCsv;

    @Bean
    public RelyingParty relyingParty() {
        Set<String> origins = Arrays.stream(originsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());

        RelyingPartyIdentity identity = RelyingPartyIdentity.builder()
                .id(rpId)
                .name(rpName)
                .build();

        return RelyingParty.builder()
                .identity(identity)
                .credentialRepository(credentialRepository)
                .origins(origins)
                .build();
    }
}

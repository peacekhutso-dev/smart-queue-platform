package com.dha.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A registered WebAuthn platform authenticator (Touch ID / Windows Hello / Android fingerprint)
 * for a STAFF or ADMIN user — the "fingerprint" Zero Trust Gate 3 credential.
 */
@Entity
@Table(name = "webauthn_credentials")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebAuthnCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Base64url-encoded credential ID, as returned by the authenticator. */
    @Column(name = "credential_id", nullable = false, unique = true, length = 1024)
    private String credentialId;

    /** Base64url-encoded COSE public key. */
    @Column(name = "public_key_cose", nullable = false, length = 2048)
    private String publicKeyCose;

    @Column(name = "signature_count", nullable = false)
    @Builder.Default
    private long signatureCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

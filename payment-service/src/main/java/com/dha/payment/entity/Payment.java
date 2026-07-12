package com.dha.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * No PAN/card data ever lives here — provider is given a hosted checkout/token
 * flow, we only ever store the provider's own session/reference ids.
 *
 * userId/queueEntryId are plain ids into other services' data (no FK) — they're
 * Long today because identity/queue still mint Long ids; this becomes a UUID
 * once identity-service does its UUID migration (see migration plan Phase 3).
 */
@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    private Long queueEntryId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(nullable = false, length = 40)
    private String provider;

    private String providerReference;

    @Column(unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = PaymentStatus.CREATED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}

package com.dha.payment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox pattern: written in the same local transaction as the domain change,
 * then relayed to RabbitMQ by OutboxRelay. Guarantees we never lose a
 * payment.succeeded event just because a broker publish failed mid-request.
 */
@Entity
@Table(name = "outbox_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String routingKey;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant publishedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}

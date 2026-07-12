package com.dha.queue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "queue_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** No FK — identity-service owns the users table now. Plain id reference. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** No FK — branch-service owns the branches table now. Plain id reference. */
    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "ticket_number", nullable = false)
    private String ticketNumber;

    /** Catalog service code (catalog-service's ServiceOffering.code) — plain reference, no FK, same convention as userId/branchId above. */
    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private QueueStatus status = QueueStatus.WAITING;

    @Column(name = "position_in_queue")
    private Integer positionInQueue;

    @Column(name = "counter_number")
    private String counterNumber;

    @Column(name = "estimated_wait_minutes")
    private Integer estimatedWaitMinutes;

    @Column(name = "qr_code")
    private String qrCode;

    @Column(name = "called_at")
    private LocalDateTime calledAt;

    @Column(name = "served_at")
    private LocalDateTime servedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "joined_at", updatable = false)
    private LocalDateTime joinedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum QueueStatus {
        WAITING, CALLED, SERVING, COMPLETE, CANCELLED
    }
}
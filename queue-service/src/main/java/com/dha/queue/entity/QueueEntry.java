package com.dha.queue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "queue_entries", indexes = {
        // findByBranchIdAndStatusIn*, countByBranchIdAndStatus*, findActiveQueueForBranch
        @Index(name = "idx_queue_entries_branch_status", columnList = "branch_id, status"),
        // findByUserIdAndStatusIn, findByUserIdOrderByJoinedAtDesc
        @Index(name = "idx_queue_entries_user_status", columnList = "user_id, status"),
        // findMaxTicketNumberForBranchToday
        @Index(name = "idx_queue_entries_branch_date", columnList = "branch_id, ticket_date")
}, uniqueConstraints = {
        // Ticket numbers reset daily per branch (see generateTicketNumber), so
        // the natural key needs ticket_date, not just branch_id — this is what
        // makes the DB (not just application logic) reject two concurrent
        // joins that both computed the same next ticket number.
        @UniqueConstraint(name = "uk_queue_entries_branch_date_ticket", columnNames = {"branch_id", "ticket_date", "ticket_number"})
})
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

    // Backs uk_queue_entries_branch_date_ticket — ticket numbers reset daily
    // per branch, so the day has to be part of the uniqueness key.
    // columnDefinition carries an explicit DB-side default: without it,
    // Hibernate's generated `ALTER TABLE ... ADD COLUMN ... NOT NULL` has no
    // default to backfill any pre-existing rows with, and MySQL strict mode
    // rejects the implicit '0000-00-00' it would otherwise try — breaking
    // ddl-auto update on any table that already has queue entries in it.
    @Column(name = "ticket_date", nullable = false, updatable = false, columnDefinition = "date not null default (curdate())")
    private LocalDate ticketDate;

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

    @PrePersist
    void onCreate() {
        if (ticketDate == null) ticketDate = LocalDate.now();
    }

    public enum QueueStatus {
        WAITING, CALLED, SERVING, COMPLETE, CANCELLED
    }
}
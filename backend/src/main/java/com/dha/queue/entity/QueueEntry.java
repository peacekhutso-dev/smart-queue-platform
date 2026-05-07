package com.dha.queue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    @ToString.Exclude
    private Branch branch;

    @Column(name = "ticket_number", nullable = false)
    private String ticketNumber;

    @Column(name = "service_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ServiceType serviceType;

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

    public enum ServiceType {
        SMART_ID("Smart ID Card", 3, 5),
        PASSPORT("Passport", 2, 3),
        BIRTH_CERTIFICATE("Birth Certificate", 7, 10),
        MARRIAGE_CERTIFICATE("Marriage Certificate", 7, 10),
        GENERAL_ENQUIRY("General Enquiry", 0, 0);

        public final String displayName;
        public final int processingWeeksMin;
        public final int processingWeeksMax;

        ServiceType(String displayName, int min, int max) {
            this.displayName = displayName;
            this.processingWeeksMin = min;
            this.processingWeeksMax = max;
        }
    }
}
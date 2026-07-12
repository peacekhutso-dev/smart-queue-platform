package com.dha.feedback.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** No FK — identity-service owns the users table now. Plain id reference. */
    @Column(name = "user_id")
    private UUID userId;

    /** No FK — branch-service owns the branches table now. Plain id reference. */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "queue_entry_id")
    private Long queueEntryId;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(name = "staff_helpful")
    private Boolean staffHelpful;

    @Column(name = "emotion_rating")
    private Integer emotionRating;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "service_type")
    private String serviceType;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;
}
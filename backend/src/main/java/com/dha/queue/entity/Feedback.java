package com.dha.queue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    @ToString.Exclude
    private Branch branch;

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
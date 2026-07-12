package com.dha.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_offerings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_type_id", nullable = false)
    private ApplicantType applicantType;

    /** Stable identifier used by the frontend and by queue-service's QueueEntry.serviceType. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(name = "fee_min", precision = 10, scale = 2)
    private BigDecimal feeMin;

    @Column(name = "fee_max", precision = 10, scale = 2)
    private BigDecimal feeMax;

    @Builder.Default
    @Column(length = 3, nullable = false)
    private String currency = "ZAR";

    /** Freeform caveat, e.g. "Excludes applicable VFS Global service charges". */
    @Column(name = "fee_note", length = 255)
    private String feeNote;

    @Column(name = "estimated_turnaround")
    private String estimatedTurnaround;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum FeeType {
        FREE, FIXED, RANGE, VARIES
    }
}

package com.dha.catalog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_requirements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_offering_id", nullable = false)
    private ServiceOffering serviceOffering;

    /** Must match one of document-service's Document.DocumentType enum names. */
    @Column(name = "document_category", nullable = false)
    private String documentCategory;

    @Column(nullable = false)
    private String label;

    @Builder.Default
    @Column(nullable = false)
    private Boolean required = true;

    @Column(name = "condition_note")
    private String conditionNote;

    @Column(name = "display_order")
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

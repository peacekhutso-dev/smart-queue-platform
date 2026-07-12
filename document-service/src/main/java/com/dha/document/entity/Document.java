package com.dha.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** No FK — identity-service owns the users table now. Plain id reference. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "file_type")
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_path")
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type")
    private DocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status")
    @Builder.Default
    private ValidationStatus validationStatus = ValidationStatus.PENDING;

    @Column(name = "validation_notes", length = 1000)
    private String validationNotes;

    /** 0-100 composite score from ml-service's blur/brightness/glare checks; null if never assessed (e.g. non-image, ml-service unavailable). */
    @Column(name = "quality_score")
    private Integer qualityScore;

    /** SHA-256 hex of the original (pre-optimization) upload bytes — fraud check: same file under a different userId. */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /** Raw JSON of ml-service's OCR field extraction (e.g. idNumber, fullName); null if not classified/extracted. */
    @Column(name = "extracted_fields", length = 2000)
    private String extractedFields;

    @CreationTimestamp
    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    public enum DocumentType {
        SA_ID,   // legacy — kept for existing rows; new uploads should prefer SA_SMART_ID/GREEN_BARCODED_ID_BOOK
        SA_SMART_ID, GREEN_BARCODED_ID_BOOK,
        BIRTH_CERTIFICATE, MARRIAGE_CERTIFICATE, DEATH_CERTIFICATE,
        PASSPORT, FOREIGN_PASSPORT, VISA_DOCUMENTS,
        PROOF_OF_RESIDENCE, UTILITY_BILL, BANK_STATEMENT,
        POLICE_CLEARANCE_CERTIFICATE, MEDICAL_REPORT, AFFIDAVIT,
        OTHER
    }

    public enum ValidationStatus {
        PENDING, VALID, REJECTED, IN_REVIEW
    }
}
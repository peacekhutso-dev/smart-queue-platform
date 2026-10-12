package com.dha.document.repository;

import com.dha.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByUserIdOrderByUploadedAtDesc(UUID userId);

    /**
     * Fraud check: same file hash already submitted under a different account.
     * A boolean exists-check rather than findBy — the same other account can
     * legitimately have more than one document with this hash (e.g. they
     * re-uploaded too), and a single-result query would throw on more than
     * one match.
     */
    boolean existsByFileHashAndUserIdNot(String fileHash, UUID userId);

    /**
     * The citizen's photo-bearing, passed-validation ID documents (SA ID,
     * passport, ...), newest first — used to serve the DHA record photo for
     * face verification. Filtered down to the newest single one in
     * DocumentService, not here, since only image uploads (not PDFs) can
     * actually be face-matched.
     */
    List<Document> findByUserIdAndDocumentTypeInAndValidationStatusOrderByUploadedAtDesc(
            UUID userId, List<Document.DocumentType> documentTypes, Document.ValidationStatus validationStatus);

    /** Every passed-validation document a citizen has on file — used to check catalog-service's per-service document requirements before queue-service lets them book. */
    List<Document> findByUserIdAndValidationStatus(UUID userId, Document.ValidationStatus validationStatus);
}
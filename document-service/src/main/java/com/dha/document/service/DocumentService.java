package com.dha.document.service;

import com.dha.document.client.MlServiceClient;
import com.dha.document.client.MlServiceClient.ClassificationResult;
import com.dha.document.client.MlServiceClient.QualityResult;
import com.dha.document.dto.response.DocumentResponse;
import com.dha.document.entity.Document;
import com.dha.document.repository.DocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentOptimizationService documentOptimizationService;
    private final MlServiceClient mlServiceClient;
    private final ObjectMapper objectMapper;

    private static final String UPLOAD_DIR = "uploads/documents/";
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of("application/pdf", "image/jpeg", "image/png", "image/jpg");

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, String documentType, UUID userId) throws IOException {
        // Validation
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("File exceeds 10MB limit");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Only PDF, JPG, PNG allowed");
        }

        byte[] originalBytes = file.getBytes();
        String fileHash = sha256Hex(originalBytes);

        // Fraud check: same file, byte-for-byte, already submitted under a
        // different account. Re-uploading your own identical document (e.g.
        // retrying a failed submission) is normal and must not be flagged —
        // only a hash match against a *different* userId counts as suspicious.
        boolean duplicateAcrossAccounts = documentRepository.existsByFileHashAndUserIdNot(fileHash, userId);

        // Optimize before storing: resize/compress images, recompress PDF streams.
        // Fails open (returns original bytes) if optimization errors out.
        byte[] storedBytes = contentType.equals("application/pdf")
                ? documentOptimizationService.optimizePdf(originalBytes)
                : documentOptimizationService.optimizeImage(originalBytes, contentType);

        if (storedBytes.length != originalBytes.length) {
            log.info("Optimized {} from {} bytes to {} bytes", file.getOriginalFilename(),
                    originalBytes.length, storedBytes.length);
        }

        // Store file
        Path uploadPath = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadPath);
        String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(uniqueName);
        Files.write(filePath, storedBytes);

        Document.DocumentType docType;
        try {
            docType = Document.DocumentType.valueOf(documentType);
        } catch (Exception e) {
            docType = Document.DocumentType.OTHER;
        }

        // Lightweight validation
        Document.ValidationStatus validationStatus = Document.ValidationStatus.IN_REVIEW;
        String validationNotes = "Document received and queued for review.";
        Integer qualityScore = null;
        String extractedFieldsJson = null;

        if (duplicateAcrossAccounts) {
            // Already flagged as a likely duplicate submission — no point spending
            // an AI classification/quality call on a document we're rejecting anyway.
            validationStatus = Document.ValidationStatus.REJECTED;
            validationNotes = "Duplicate submission detected: this exact document has already been submitted under a different account.";
        } else {
            // AI classification: does the uploaded file actually match the type the
            // citizen selected? Runs for every file type (Claude handles PDFs
            // natively, unlike the quality gate below which needs raw pixel data).
            ClassificationResult classification = mlServiceClient.classifyDocument(
                    storedBytes, file.getOriginalFilename(), docType.name());

            if (!classification.verified()) {
                validationStatus = Document.ValidationStatus.REJECTED;
                validationNotes = classification.message();
            } else {
                if (!classification.extractedFields().isEmpty()) {
                    extractedFieldsJson = writeJson(classification.extractedFields());
                }

                if (contentType.startsWith("image/")) {
                    validationStatus = Document.ValidationStatus.VALID;
                    validationNotes = "Image document received successfully.";

                    QualityResult quality = mlServiceClient.assessQuality(storedBytes, file.getOriginalFilename());
                    qualityScore = quality.score();
                    if (!quality.passed()) {
                        validationStatus = Document.ValidationStatus.REJECTED;
                        validationNotes = "Document quality too low (score: " + quality.score()
                                + "%). Please upload a clearer image. Issues: " + String.join(", ", quality.issues());
                    }
                }
            }
        }

        Document document = Document.builder()
                .userId(userId)
                .fileName(uniqueName)
                .originalName(file.getOriginalFilename())
                .fileType(contentType)
                .fileSize((long) storedBytes.length)
                .filePath(filePath.toString())
                .documentType(docType)
                .validationStatus(validationStatus)
                .validationNotes(validationNotes)
                .qualityScore(qualityScore)
                .fileHash(fileHash)
                .extractedFields(extractedFieldsJson)
                .build();

        document = documentRepository.save(document);
        return mapToResponse(document);
    }

    public List<DocumentResponse> getMyDocuments(UUID userId) {
        return documentRepository.findByUserIdOrderByUploadedAtDesc(userId)
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void deleteDocument(Long id, UUID userId) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!doc.getUserId().equals(userId)) {
            throw new SecurityException("Not authorised to delete this document");
        }
        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException e) {
            log.warn("Could not delete file: {}", doc.getFilePath());
        }
        documentRepository.delete(doc);
    }

    private DocumentResponse mapToResponse(Document doc) {
        return DocumentResponse.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .originalName(doc.getOriginalName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .documentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : null)
                .validationStatus(doc.getValidationStatus().name())
                .validationNotes(doc.getValidationNotes())
                .qualityScore(doc.getQualityScore())
                .extractedFields(readJson(doc.getExtractedFields()))
                .uploadedAt(doc.getUploadedAt())
                .build();
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String writeJson(Map<String, String> fields) {
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            log.warn("Failed to serialize extracted fields: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readJson(String json) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize extracted fields: {}", e.getMessage());
            return null;
        }
    }
}
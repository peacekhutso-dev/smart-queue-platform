package com.dha.queue.service;

import com.dha.queue.dto.response.DocumentResponse;
import com.dha.queue.entity.Document;
import com.dha.queue.entity.User;
import com.dha.queue.repository.DocumentRepository;
import com.dha.queue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    private static final String UPLOAD_DIR = "uploads/documents/";
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_TYPES = List.of("application/pdf", "image/jpeg", "image/png", "image/jpg");

    @Transactional
    public DocumentResponse uploadDocument(MultipartFile file, String documentType, String userEmail) throws IOException {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Validation
        if (file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("File exceeds 10MB limit");
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Invalid file type. Only PDF, JPG, PNG allowed");
        }

        // Store file
        Path uploadPath = Paths.get(UPLOAD_DIR);
        Files.createDirectories(uploadPath);
        String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(uniqueName);
        Files.write(filePath, file.getBytes());

        Document.DocumentType docType;
        try {
            docType = Document.DocumentType.valueOf(documentType);
        } catch (Exception e) {
            docType = Document.DocumentType.OTHER;
        }

        // Lightweight validation
        Document.ValidationStatus validationStatus = Document.ValidationStatus.IN_REVIEW;
        String validationNotes = "Document received and queued for review.";

        if (contentType.startsWith("image/")) {
            validationStatus = Document.ValidationStatus.VALID;
            validationNotes = "Image document received successfully.";
        }

        Document document = Document.builder()
                .user(user)
                .fileName(uniqueName)
                .originalName(file.getOriginalFilename())
                .fileType(contentType)
                .fileSize(file.getSize())
                .filePath(filePath.toString())
                .documentType(docType)
                .validationStatus(validationStatus)
                .validationNotes(validationNotes)
                .build();

        document = documentRepository.save(document);
        return mapToResponse(document);
    }

    public List<DocumentResponse> getMyDocuments(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return documentRepository.findByUserIdOrderByUploadedAtDesc(user.getId())
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional
    public void deleteDocument(Long id, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found"));
        if (!doc.getUser().getId().equals(user.getId())) {
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
                .uploadedAt(doc.getUploadedAt())
                .build();
    }
}
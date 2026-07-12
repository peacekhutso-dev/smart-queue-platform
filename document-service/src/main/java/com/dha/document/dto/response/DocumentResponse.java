package com.dha.document.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Map;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentResponse {
    private Long id;
    private String fileName;
    private String originalName;
    private String fileType;
    private Long fileSize;
    private String documentType;
    private String validationStatus;
    private String validationNotes;
    private Integer qualityScore;
    private Map<String, String> extractedFields;
    private LocalDateTime uploadedAt;
}
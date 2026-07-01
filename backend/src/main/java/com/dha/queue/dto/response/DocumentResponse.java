package com.dha.queue.dto.response;

import lombok.*;
import java.time.LocalDateTime;

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
    private LocalDateTime uploadedAt;
}
package com.dha.catalog.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class DocumentRequirementResponse {
    private Long id;
    private String documentCategory;
    private String label;
    private Boolean required;
    private String conditionNote;
}

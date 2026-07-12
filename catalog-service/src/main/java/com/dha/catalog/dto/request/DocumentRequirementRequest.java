package com.dha.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DocumentRequirementRequest {

    @NotBlank(message = "documentCategory is required")
    private String documentCategory;

    @NotBlank(message = "label is required")
    private String label;

    private Boolean required;

    private String conditionNote;

    private Integer displayOrder;
}

package com.dha.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ApplicantTypeRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "displayName is required")
    private String displayName;

    private String description;

    private Integer displayOrder;

    private Boolean active;
}

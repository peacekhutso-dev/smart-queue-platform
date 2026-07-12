package com.dha.catalog.dto.request;

import com.dha.catalog.entity.ServiceOffering.FeeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceOfferingRequest {

    @NotBlank(message = "applicantTypeCode is required")
    private String applicantTypeCode;

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String description;

    @NotNull(message = "feeType is required")
    private FeeType feeType;

    private BigDecimal feeMin;

    private BigDecimal feeMax;

    private String currency;

    private String feeNote;

    private String estimatedTurnaround;

    private Integer displayOrder;

    private Boolean active;
}

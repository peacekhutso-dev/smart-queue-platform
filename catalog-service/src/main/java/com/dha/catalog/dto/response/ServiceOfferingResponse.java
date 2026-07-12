package com.dha.catalog.dto.response;

import com.dha.catalog.entity.ServiceOffering.FeeType;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ServiceOfferingResponse {
    private Long id;
    private String applicantTypeCode;
    private String code;
    private String name;
    private String description;
    private FeeType feeType;
    private BigDecimal feeMin;
    private BigDecimal feeMax;
    private String currency;
    private String feeNote;
    /** Server-computed so the frontend never reimplements fee formatting: "Free" / "R140" / "R70–R140" / "Varies". */
    private String feeDisplay;
    private String estimatedTurnaround;
    private List<DocumentRequirementResponse> documents;
}

package com.dha.catalog.dto.response;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ApplicantTypeResponse {
    private Long id;
    private String code;
    private String displayName;
    private String description;
    private List<ServiceOfferingResponse> services;
}

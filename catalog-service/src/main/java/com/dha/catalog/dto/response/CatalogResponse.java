package com.dha.catalog.dto.response;

import lombok.*;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CatalogResponse {
    private List<ApplicantTypeResponse> applicantTypes;
}

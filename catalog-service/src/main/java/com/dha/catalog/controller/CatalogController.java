package com.dha.catalog.controller;

import com.dha.catalog.dto.response.ApiResponse;
import com.dha.catalog.dto.response.ApplicantTypeResponse;
import com.dha.catalog.dto.response.CatalogResponse;
import com.dha.catalog.dto.response.DocumentRequirementResponse;
import com.dha.catalog.dto.response.ServiceOfferingResponse;
import com.dha.catalog.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<ApiResponse<CatalogResponse>> getFullCatalog() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getFullCatalog()));
    }

    @GetMapping("/applicant-types")
    public ResponseEntity<ApiResponse<List<ApplicantTypeResponse>>> getApplicantTypes() {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getApplicantTypes()));
    }

    @GetMapping("/applicant-types/{code}/services")
    public ResponseEntity<ApiResponse<List<ServiceOfferingResponse>>> getServicesForApplicantType(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getServicesForApplicantType(code)));
    }

    @GetMapping("/services/{code}")
    public ResponseEntity<ApiResponse<ServiceOfferingResponse>> getServiceByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getServiceByCode(code)));
    }

    @GetMapping("/services/{code}/documents")
    public ResponseEntity<ApiResponse<List<DocumentRequirementResponse>>> getDocumentsForService(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(catalogService.getDocumentsForService(code)));
    }
}

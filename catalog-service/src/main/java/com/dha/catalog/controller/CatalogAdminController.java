package com.dha.catalog.controller;

import com.dha.catalog.dto.request.ApplicantTypeRequest;
import com.dha.catalog.dto.request.DocumentRequirementRequest;
import com.dha.catalog.dto.request.ServiceOfferingRequest;
import com.dha.catalog.dto.response.ApiResponse;
import com.dha.catalog.security.JwtPrincipal;
import com.dha.catalog.service.CatalogAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogAdminController {

    private final CatalogAdminService catalogAdminService;

    @PostMapping("/applicant-types")
    public ResponseEntity<ApiResponse<Object>> createApplicantType(
            @Valid @RequestBody ApplicantTypeRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Applicant type created",
                catalogAdminService.createApplicantType(request, principal)));
    }

    @PutMapping("/applicant-types/{id}")
    public ResponseEntity<ApiResponse<Object>> updateApplicantType(
            @PathVariable Long id,
            @Valid @RequestBody ApplicantTypeRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Applicant type updated",
                catalogAdminService.updateApplicantType(id, request, principal)));
    }

    @PostMapping("/services")
    public ResponseEntity<ApiResponse<Object>> createService(
            @Valid @RequestBody ServiceOfferingRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Service created",
                catalogAdminService.createService(request, principal)));
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<ApiResponse<Object>> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceOfferingRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Service updated",
                catalogAdminService.updateService(id, request, principal)));
    }

    @DeleteMapping("/services/{id}")
    public ResponseEntity<ApiResponse<Object>> deactivateService(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        catalogAdminService.deactivateService(id, principal);
        return ResponseEntity.ok(ApiResponse.ok("Service deactivated", null));
    }

    @PostMapping("/services/{serviceId}/documents")
    public ResponseEntity<ApiResponse<Object>> addDocumentRequirement(
            @PathVariable Long serviceId,
            @Valid @RequestBody DocumentRequirementRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Document requirement added",
                catalogAdminService.addDocumentRequirement(serviceId, request, principal)));
    }

    @PutMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<Object>> updateDocumentRequirement(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequirementRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok("Document requirement updated",
                catalogAdminService.updateDocumentRequirement(id, request, principal)));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<ApiResponse<Object>> deleteDocumentRequirement(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        catalogAdminService.deleteDocumentRequirement(id, principal);
        return ResponseEntity.ok(ApiResponse.ok("Document requirement deleted", null));
    }
}

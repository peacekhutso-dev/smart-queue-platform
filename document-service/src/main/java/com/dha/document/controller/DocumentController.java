package com.dha.document.controller;

import com.dha.document.dto.response.ApiResponse;
import com.dha.document.dto.response.DocumentResponse;
import com.dha.document.security.JwtPrincipal;
import com.dha.document.service.DocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @AuthenticationPrincipal JwtPrincipal principal) throws IOException {
        DocumentResponse response = documentService.uploadDocument(file, documentType, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Document uploaded successfully", response));
    }

    @GetMapping("/my-docs")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocs(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(documentService.getMyDocuments(principal.userId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtPrincipal principal) {
        documentService.deleteDocument(id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Document deleted", null));
    }
}

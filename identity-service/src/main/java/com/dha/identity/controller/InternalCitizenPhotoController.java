package com.dha.identity.controller;

import com.dha.identity.dto.response.ApiResponse;
import com.dha.identity.service.CitizenRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * Service-to-service only: verification-service fetches the Home Affairs
 * photo a live face scan is compared against. The path sits outside /api/**,
 * so the Gateway has no route to it; GatewaySecretFilter additionally
 * requires X-Gateway-Secret on every /internal/** request.
 */
@RestController
@RequestMapping("/internal/citizen-photo")
@RequiredArgsConstructor
public class InternalCitizenPhotoController {

    private final CitizenRecordService citizenRecordService;

    @GetMapping
    public ResponseEntity<?> getForUser(@RequestParam UUID userId) {
        return citizenRecordService.getPhotoForUser(userId)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(ApiResponse.ok(Map.of(
                        "imageBase64", Base64.getEncoder().encodeToString(p.getPhoto()),
                        "contentType", p.getContentType()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("No Home Affairs photo on file")));
    }
}

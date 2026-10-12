package com.dha.identity.controller;

import com.dha.identity.dto.request.CitizenRecordRequest;
import com.dha.identity.dto.response.ApiResponse;
import com.dha.identity.dto.response.CitizenRecordResponse;
import com.dha.identity.service.CitizenRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * DEV ONLY: the admin dashboard's "Citizen Records" tool for loading Home
 * Affairs records + ID photos into this prototype. Reached through the
 * Gateway as a FULL-tier route (/api/auth/admin/** isn't in GATE1_ONLY), so
 * the caller has passed 2FA and the fingerprint gate; this controller then
 * requires the forwarded role to be ADMIN. GatewaySecretFilter guarantees
 * X-User-Role came from the Gateway.
 */
@RestController
@RequestMapping("/api/auth/admin/citizen-records")
@RequiredArgsConstructor
public class CitizenRecordAdminController {

    private final CitizenRecordService citizenRecordService;

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader("X-User-Role") String role) {
        if (!isAdmin(role)) return forbidden();
        List<CitizenRecordResponse> records = citizenRecordService.list();
        return ResponseEntity.ok(ApiResponse.ok(records));
    }

    @PutMapping
    public ResponseEntity<?> save(@RequestHeader("X-User-Role") String role,
                                  @Valid @RequestBody CitizenRecordRequest request) {
        if (!isAdmin(role)) return forbidden();
        return ResponseEntity.ok(ApiResponse.ok("Citizen record saved", citizenRecordService.save(request)));
    }

    @PutMapping(value = "/{saIdNumber}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> savePhoto(@RequestHeader("X-User-Role") String role,
                                       @PathVariable String saIdNumber,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        if (!isAdmin(role)) return forbidden();
        citizenRecordService.savePhoto(saIdNumber, file);
        return ResponseEntity.ok(ApiResponse.ok("Photo saved", null));
    }

    @GetMapping("/{saIdNumber}/photo")
    public ResponseEntity<?> getPhoto(@RequestHeader("X-User-Role") String role,
                                      @PathVariable String saIdNumber) {
        if (!isAdmin(role)) return forbidden();
        return citizenRecordService.getPhoto(saIdNumber)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .body(p.getPhoto()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("No photo on file")));
    }

    @DeleteMapping("/{saIdNumber}/photo")
    public ResponseEntity<?> deletePhoto(@RequestHeader("X-User-Role") String role,
                                         @PathVariable String saIdNumber) {
        if (!isAdmin(role)) return forbidden();
        citizenRecordService.deletePhoto(saIdNumber);
        return ResponseEntity.ok(ApiResponse.ok("Photo removed", null));
    }

    private boolean isAdmin(String role) {
        return "ADMIN".equals(role);
    }

    // 403, not 401 — the admin dashboard treats 401 as "session invalid".
    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Admin access required"));
    }
}

package com.dha.queue.controller;

import com.dha.queue.dto.response.ApiResponse;
import com.dha.queue.dto.response.BranchResponse;
import com.dha.queue.service.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getAllBranches() {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getAllBranches()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BranchResponse>> getBranch(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getBranchById(id)));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getNearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "50") double radius) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getNearbyBranches(lat, lng, radius)));
    }

    @GetMapping("/province/{province}")
    public ResponseEntity<ApiResponse<List<BranchResponse>>> getByProvince(@PathVariable String province) {
        return ResponseEntity.ok(ApiResponse.ok(branchService.getByProvince(province)));
    }
}
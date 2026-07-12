package com.dha.queue.controller;

import com.dha.queue.dto.request.QueueJoinRequest;
import com.dha.queue.dto.response.ApiResponse;
import com.dha.queue.dto.response.BranchStatsResponse;
import com.dha.queue.dto.response.QueueEntryResponse;
import com.dha.queue.security.JwtPrincipal;
import com.dha.queue.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> joinQueue(
            @Valid @RequestBody QueueJoinRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        QueueEntryResponse entry = queueService.joinQueue(request, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Successfully joined queue", entry));
    }

    @GetMapping("/my-ticket")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> getMyTicket(
            @AuthenticationPrincipal JwtPrincipal principal) {
        QueueEntryResponse ticket = queueService.getMyTicket(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(ticket));
    }

    @DeleteMapping("/{ticketId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        queueService.cancelTicket(ticketId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Ticket cancelled", null));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<QueueEntryResponse>>> getBranchQueue(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getBranchQueue(branchId)));
    }

    // Admin dashboard stats tiles — staff/admin only
    @GetMapping("/branch/{branchId}/stats")
    public ResponseEntity<ApiResponse<BranchStatsResponse>> getBranchStats(
            @PathVariable Long branchId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        requireStaff(principal);
        return ResponseEntity.ok(ApiResponse.ok(queueService.getBranchStats(branchId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<QueueEntryResponse>>> getHistory(
            @AuthenticationPrincipal JwtPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getMyHistory(principal.userId())));
    }

    // Staff endpoint to advance ticket status
    @PutMapping("/{entryId}/advance")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> advanceTicket(
            @PathVariable Long entryId,
            @RequestParam String status,
            @RequestParam(required = false) String counter,
            @AuthenticationPrincipal JwtPrincipal principal) {
        requireStaff(principal);
        QueueEntryResponse entry = queueService.advanceTicket(entryId, status, counter);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", entry));
    }

    private void requireStaff(JwtPrincipal principal) {
        boolean isStaff = principal != null && ("STAFF".equals(principal.role()) || "ADMIN".equals(principal.role()));
        if (!isStaff) {
            throw new SecurityException("Staff or admin role required");
        }
    }
}

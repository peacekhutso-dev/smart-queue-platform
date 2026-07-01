package com.dha.queue.controller;

import com.dha.queue.dto.request.QueueJoinRequest;
import com.dha.queue.dto.response.ApiResponse;
import com.dha.queue.dto.response.QueueEntryResponse;
import com.dha.queue.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
            @AuthenticationPrincipal UserDetails userDetails) {
        QueueEntryResponse entry = queueService.joinQueue(request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Successfully joined queue", entry));
    }

    @GetMapping("/my-ticket")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> getMyTicket(
            @AuthenticationPrincipal UserDetails userDetails) {
        QueueEntryResponse ticket = queueService.getMyTicket(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok(ticket));
    }

    @DeleteMapping("/{ticketId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserDetails userDetails) {
        queueService.cancelTicket(ticketId, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Ticket cancelled", null));
    }

    @GetMapping("/branch/{branchId}")
    public ResponseEntity<ApiResponse<List<QueueEntryResponse>>> getBranchQueue(@PathVariable Long branchId) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getBranchQueue(branchId)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<QueueEntryResponse>>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(queueService.getMyHistory(userDetails.getUsername())));
    }

    // Staff endpoint to advance ticket status
    @PutMapping("/{entryId}/advance")
    public ResponseEntity<ApiResponse<QueueEntryResponse>> advanceTicket(
            @PathVariable Long entryId,
            @RequestParam String status,
            @RequestParam(required = false) String counter) {
        QueueEntryResponse entry = queueService.advanceTicket(entryId, status, counter);
        return ResponseEntity.ok(ApiResponse.ok("Status updated", entry));
    }
}
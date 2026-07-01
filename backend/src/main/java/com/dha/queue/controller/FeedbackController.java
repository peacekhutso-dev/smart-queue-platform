package com.dha.queue.controller;

import com.dha.queue.dto.request.FeedbackRequest;
import com.dha.queue.dto.response.ApiResponse;
import com.dha.queue.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> submit(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        feedbackService.submitFeedback(request, userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Feedback submitted. Thank you!", null));
    }
}
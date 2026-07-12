package com.dha.feedback.controller;

import com.dha.feedback.dto.request.FeedbackRequest;
import com.dha.feedback.dto.response.ApiResponse;
import com.dha.feedback.security.JwtPrincipal;
import com.dha.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> submit(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {
        feedbackService.submitFeedback(request, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok("Feedback submitted. Thank you!", null));
    }
}

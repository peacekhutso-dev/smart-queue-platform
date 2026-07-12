package com.dha.payment.controller;

import com.dha.payment.dto.request.CreatePaymentIntentRequest;
import com.dha.payment.dto.response.ApiResponse;
import com.dha.payment.dto.response.PaymentResponse;
import com.dha.payment.entity.Payment;
import com.dha.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/intents")
    public ResponseEntity<ApiResponse<PaymentResponse>> createIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request) {
        var result = paymentService.createIntent(request);
        return ResponseEntity.ok(ApiResponse.ok(PaymentResponse.of(result.payment(), result.redirectUrl())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable UUID id) {
        Payment payment = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.ok(PaymentResponse.of(payment)));
    }

    /**
     * No JWT/zero-trust gate here — the Gateway exempts this path. Auth is
     * the provider's own webhook signature, verified inside the service.
     */
    @PostMapping(value = "/webhooks/{provider}", consumes = "application/json")
    public ResponseEntity<Void> handleWebhook(
            @PathVariable String provider,
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signatureHeader) {
        paymentService.handleWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok().build();
    }
}

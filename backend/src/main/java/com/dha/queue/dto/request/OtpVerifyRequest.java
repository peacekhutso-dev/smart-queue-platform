package com.dha.queue.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class OtpVerifyRequest {
    @NotBlank
    private String identifier;
    @NotBlank
    private String otp;
}
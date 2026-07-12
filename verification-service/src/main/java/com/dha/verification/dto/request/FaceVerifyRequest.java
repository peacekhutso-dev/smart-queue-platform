package com.dha.verification.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FaceVerifyRequest {
    @NotBlank(message = "Image data is required")
    private String imageBase64;
}

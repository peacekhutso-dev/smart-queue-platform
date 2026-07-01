package com.dha.queue.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IdVerifyRequest {
    @NotBlank
    @Pattern(regexp = "\\d{13}", message = "SA ID must be exactly 13 digits")
    private String saIdNumber;
}
package com.dha.identity.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type;
    private UUID userId;
    private String email;
    private String firstName;
    private String lastName;
    private String role;
    private Boolean isVerified;
    private Boolean isIdVerified;
    private String phoneNumber;
    private String province;
    private Long assignedBranchId;
}

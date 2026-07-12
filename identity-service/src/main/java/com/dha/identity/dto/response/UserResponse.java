package com.dha.identity.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private String saIdNumber;
    private String role;
    private Boolean isVerified;
    private Boolean isIdVerified;
    private String province;
    private LocalDateTime createdAt;
}

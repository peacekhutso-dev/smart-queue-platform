package com.dha.queue.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserResponse {
    private Long id;
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
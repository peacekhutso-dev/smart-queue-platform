package com.dha.queue.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QueueEntryResponse {
    private Long id;
    private String ticketNumber;
    private String serviceType;
    private String status;
    private Integer positionInQueue;
    private Integer estimatedWaitMinutes;
    private String counterNumber;
    private String qrCode;
    // data: URI PNG rendered from qrCode — see QrCodeService.
    private String qrCodeImage;
    private Long branchId;
    private String branchName;
    private String branchAddress;
    private LocalDateTime joinedAt;
    private LocalDateTime calledAt;
    private LocalDateTime completedAt;
    // Live branch data
    private Long totalInQueue;
}
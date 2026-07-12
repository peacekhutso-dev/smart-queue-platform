package com.dha.queue.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class QueueBroadcast {
    private Long branchId;
    private String branchName;
    private Long totalWaiting;
    private Integer estimatedWaitMinutes;
    private Integer capacityPercent;
    private String currentlyCalledTicket;
    private String currentlyCalledCounter;
    private List<QueueEntryResponse> topEntries;
    private LocalDateTime timestamp;
}
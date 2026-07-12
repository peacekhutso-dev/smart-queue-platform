package com.dha.queue.dto.response;

import lombok.*;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BranchStatsResponse {
    private long inQueue;
    private int avgWaitMinutes;
    private long totalServedToday;
    private int efficiencyPercent;
}

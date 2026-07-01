package com.dha.queue.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QueueJoinRequest {
    @NotNull
    private Long branchId;
    @NotNull
    private String serviceType;
}
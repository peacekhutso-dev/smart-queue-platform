package com.dha.queue.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackRequest {
    @NotNull
    private Long branchId;
    private Long queueEntryId;
    @NotNull
    @Min(1) @Max(5)
    private Integer overallRating;
    private Boolean staffHelpful;
    @Min(1) @Max(5)
    private Integer emotionRating;
    private String comment;
    private String serviceType;
}
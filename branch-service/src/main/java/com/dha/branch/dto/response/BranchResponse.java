package com.dha.branch.dto.response;

import lombok.*;
import java.time.LocalTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BranchResponse {
    private Long id;
    private String name;
    private String address;
    private String province;
    private String city;
    private String suburb;
    private Double latitude;
    private Double longitude;
    private String phoneNumber;
    private LocalTime openingTime;
    private LocalTime closingTime;
    private Integer maxCapacity;
    private Integer activeCounters;
    private Integer avgServiceTimeMinutes;
    private Boolean hasWifi;
    private Boolean hasParking;
    private Boolean isAccessible;
    // Live data — event-fed from queue-service's queue.events (see QueueCountCache).
    private Long currentQueueSize;
    private Integer estimatedWaitMinutes;
    private Integer capacityPercent;
    private Boolean isOpen;
    private Double distanceKm;
}

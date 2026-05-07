package com.dha.queue.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "branches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String address;

    private String province;
    private String city;
    private String suburb;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "max_capacity")
    @Builder.Default
    private Integer maxCapacity = 100;

    @Column(name = "active_counters")
    @Builder.Default
    private Integer activeCounters = 5;

    @Column(name = "avg_service_time_minutes")
    @Builder.Default
    private Integer avgServiceTimeMinutes = 8;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "has_wifi")
    @Builder.Default
    private Boolean hasWifi = false;

    @Column(name = "has_parking")
    @Builder.Default
    private Boolean hasParking = false;

    @Column(name = "is_accessible")
    @Builder.Default
    private Boolean isAccessible = true;

    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<QueueEntry> queueEntries;
}
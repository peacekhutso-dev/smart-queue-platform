package com.dha.branch.service;

import com.dha.branch.cache.QueueCountCache;
import com.dha.branch.dto.response.BranchResponse;
import com.dha.branch.entity.Branch;
import com.dha.branch.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final QueueCountCache queueCountCache;

    public List<BranchResponse> getAllBranches() {
        Map<Long, Long> counts = queueCountCache.getCounts();
        return branchRepository.findByIsActiveTrue().stream()
                .map(branch -> mapToResponse(branch, null, null, counts))
                .toList();
    }

    public BranchResponse getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        return mapToResponse(branch, null, null, queueCountCache.getCounts());
    }

    public List<BranchResponse> getNearbyBranches(double lat, double lng, double radiusKm) {
        List<Branch> inRadius = branchRepository.findNearbyBranches(lat, lng, radiusKm, 10);
        // Nothing within the preferred radius — fall back to the closest branches anyway
        // rather than leaving the user with a dead-end "no branches" result.
        List<Branch> results = inRadius.isEmpty()
                ? branchRepository.findClosestBranches(lat, lng, 5)
                : inRadius;
        Map<Long, Long> counts = queueCountCache.getCounts();
        return results.stream()
                .map(branch -> mapToResponse(branch, lat, lng, counts))
                .toList();
    }

    private static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public List<BranchResponse> getByProvince(String province) {
        Map<Long, Long> counts = queueCountCache.getCounts();
        return branchRepository.findByProvinceAndIsActiveTrue(province).stream()
                .map(branch -> mapToResponse(branch, null, null, counts))
                .toList();
    }

    private BranchResponse mapToResponse(Branch branch, Double fromLat, Double fromLng, Map<Long, Long> counts) {
        long queueSize = counts.getOrDefault(branch.getId(), 0L);
        int waitMinutes = branch.getActiveCounters() != null && branch.getActiveCounters() > 0
                ? (int) ((queueSize * branch.getAvgServiceTimeMinutes()) / branch.getActiveCounters())
                : (int) queueSize * 8;
        int capacityPct = branch.getMaxCapacity() > 0
                ? (int) Math.min((queueSize * 100) / branch.getMaxCapacity(), 100) : 0;

        LocalTime now = LocalTime.now();
        boolean isOpen = branch.getOpeningTime() != null && branch.getClosingTime() != null
                && now.isAfter(branch.getOpeningTime()) && now.isBefore(branch.getClosingTime());

        Double distanceKm = (fromLat != null && fromLng != null && branch.getLatitude() != null && branch.getLongitude() != null)
                ? haversineKm(fromLat, fromLng, branch.getLatitude(), branch.getLongitude())
                : null;

        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .address(branch.getAddress())
                .province(branch.getProvince())
                .city(branch.getCity())
                .suburb(branch.getSuburb())
                .latitude(branch.getLatitude())
                .longitude(branch.getLongitude())
                .distanceKm(distanceKm)
                .phoneNumber(branch.getPhoneNumber())
                .openingTime(branch.getOpeningTime())
                .closingTime(branch.getClosingTime())
                .maxCapacity(branch.getMaxCapacity())
                .activeCounters(branch.getActiveCounters())
                .avgServiceTimeMinutes(branch.getAvgServiceTimeMinutes())
                .hasWifi(branch.getHasWifi())
                .hasParking(branch.getHasParking())
                .isAccessible(branch.getIsAccessible())
                .currentQueueSize(queueSize)
                .estimatedWaitMinutes(waitMinutes)
                .capacityPercent(capacityPct)
                .isOpen(isOpen)
                .build();
    }
}

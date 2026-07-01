package com.dha.queue.service;

import com.dha.queue.dto.response.BranchResponse;
import com.dha.queue.entity.Branch;
import com.dha.queue.entity.QueueEntry.QueueStatus;
import com.dha.queue.repository.BranchRepository;
import com.dha.queue.repository.QueueEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final QueueEntryRepository queueEntryRepository;

    public List<BranchResponse> getAllBranches() {
        return branchRepository.findByIsActiveTrue().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public BranchResponse getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));
        return mapToResponse(branch);
    }

    public List<BranchResponse> getNearbyBranches(double lat, double lng, double radiusKm) {
        return branchRepository.findNearbyBranches(lat, lng, radiusKm, 10).stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<BranchResponse> getByProvince(String province) {
        return branchRepository.findByProvinceAndIsActiveTrue(province).stream()
                .map(this::mapToResponse)
                .toList();
    }

    private BranchResponse mapToResponse(Branch branch) {
        List<QueueStatus> activeStatuses = List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.SERVING);
        long queueSize = queueEntryRepository.countByBranchIdAndStatusIn(branch.getId(), activeStatuses);
        int waitMinutes = branch.getActiveCounters() != null && branch.getActiveCounters() > 0
                ? (int) ((queueSize * branch.getAvgServiceTimeMinutes()) / branch.getActiveCounters())
                : (int) queueSize * 8;
        int capacityPct = branch.getMaxCapacity() > 0
                ? (int) Math.min((queueSize * 100) / branch.getMaxCapacity(), 100) : 0;

        LocalTime now = LocalTime.now();
        boolean isOpen = branch.getOpeningTime() != null && branch.getClosingTime() != null
                && now.isAfter(branch.getOpeningTime()) && now.isBefore(branch.getClosingTime());

        return BranchResponse.builder()
                .id(branch.getId())
                .name(branch.getName())
                .address(branch.getAddress())
                .province(branch.getProvince())
                .city(branch.getCity())
                .suburb(branch.getSuburb())
                .latitude(branch.getLatitude())
                .longitude(branch.getLongitude())
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
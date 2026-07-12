package com.dha.queue.service;

import com.dha.queue.client.BranchClient;
import com.dha.queue.client.BranchClient.BranchInfo;
import com.dha.queue.config.RabbitMQConfig;
import com.dha.queue.dto.request.QueueJoinRequest;
import com.dha.queue.dto.response.QueueBroadcast;
import com.dha.queue.dto.response.QueueEntryResponse;
import com.dha.queue.entity.QueueEntry;
import com.dha.queue.entity.QueueEntry.QueueStatus;
import com.dha.queue.repository.QueueEntryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final BranchClient branchClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    private static final List<QueueStatus> ACTIVE_STATUSES = List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.SERVING);

    @Transactional
    public QueueEntryResponse joinQueue(QueueJoinRequest request, UUID userId) {
        BranchInfo branch = branchClient.getBranch(request.getBranchId());

        // Check if user already in queue
        Optional<QueueEntry> existing = queueEntryRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (existing.isPresent()) {
            throw new IllegalStateException("You are already in a queue. Cancel your current ticket first.");
        }

        String serviceType = request.getServiceType();

        String ticketNumber = generateTicketNumber(branch.id());
        long position = queueEntryRepository.countByBranchIdAndStatus(branch.id(), QueueStatus.WAITING) + 1;
        int waitMinutes = calculateWaitTime(branch, (int) position);

        QueueEntry entry = QueueEntry.builder()
                .userId(userId)
                .branchId(branch.id())
                .ticketNumber(ticketNumber)
                .serviceType(serviceType)
                .status(QueueStatus.WAITING)
                .positionInQueue((int) position)
                .estimatedWaitMinutes(waitMinutes)
                .qrCode("DHA-" + ticketNumber + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();

        entry = queueEntryRepository.save(entry);

        // Broadcast update to branch topic
        broadcastQueueUpdate(branch.id());

        return mapToResponse(entry, branch);
    }

    public QueueEntryResponse getMyTicket(UUID userId) {
        return queueEntryRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                .map(entry -> mapToResponse(entry, branchClient.getBranch(entry.getBranchId())))
                .orElse(null);
    }

    @Transactional
    public void cancelTicket(Long entryId, UUID userId) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!entry.getUserId().equals(userId)) {
            throw new SecurityException("Not authorised to cancel this ticket");
        }

        entry.setStatus(QueueStatus.CANCELLED);
        queueEntryRepository.save(entry);

        broadcastQueueUpdate(entry.getBranchId());
    }

    @Transactional
    public QueueEntryResponse advanceTicket(Long entryId, String newStatus, String counterNumber) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Entry not found"));

        QueueStatus status = QueueStatus.valueOf(newStatus);
        entry.setStatus(status);

        if (status == QueueStatus.CALLED) {
            entry.setCalledAt(LocalDateTime.now());
            entry.setCounterNumber(counterNumber);
        } else if (status == QueueStatus.SERVING) {
            entry.setServedAt(LocalDateTime.now());
        } else if (status == QueueStatus.COMPLETE) {
            entry.setCompletedAt(LocalDateTime.now());
        }

        entry = queueEntryRepository.save(entry);
        BranchInfo branch = branchClient.getBranch(entry.getBranchId());

        // Notify the specific user
        messagingTemplate.convertAndSend(
                "/topic/user/" + entry.getUserId() + "/status",
                mapToResponse(entry, branch)
        );

        broadcastQueueUpdate(entry.getBranchId());
        return mapToResponse(entry, branch);
    }

    public com.dha.queue.dto.response.BranchStatsResponse getBranchStats(Long branchId) {
        BranchInfo branch = branchClient.getBranch(branchId);

        long waiting = queueEntryRepository.countByBranchIdAndStatus(branchId, QueueStatus.WAITING);
        int waitMinutes = calculateWaitTime(branch, (int) waiting);

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        LocalDateTime now = LocalDateTime.now();
        long servedToday = queueEntryRepository.countByBranchIdAndStatusAndJoinedAtBetween(
                branchId, QueueStatus.COMPLETE, startOfDay, now);
        long cancelledToday = queueEntryRepository.countByBranchIdAndStatusAndJoinedAtBetween(
                branchId, QueueStatus.CANCELLED, startOfDay, now);

        long finishedToday = servedToday + cancelledToday;
        int efficiencyPercent = finishedToday == 0
                ? 100
                : (int) Math.round(servedToday * 100.0 / finishedToday);

        return com.dha.queue.dto.response.BranchStatsResponse.builder()
                .inQueue(waiting)
                .avgWaitMinutes(waitMinutes)
                .totalServedToday(servedToday)
                .efficiencyPercent(efficiencyPercent)
                .build();
    }

    public List<QueueEntryResponse> getBranchQueue(Long branchId) {
        BranchInfo branch = branchClient.getBranch(branchId);
        return queueEntryRepository.findByBranchIdAndStatusInOrderByJoinedAtAsc(branchId, ACTIVE_STATUSES)
                .stream()
                .map(entry -> mapToResponse(entry, branch))
                .toList();
    }

    public List<QueueEntryResponse> getMyHistory(UUID userId) {
        Map<Long, BranchInfo> branchCache = new HashMap<>();
        return queueEntryRepository.findByUserIdOrderByJoinedAtDesc(userId)
                .stream()
                .map(entry -> mapToResponse(entry,
                        branchCache.computeIfAbsent(entry.getBranchId(), branchClient::getBranch)))
                .toList();
    }

    public void broadcastQueueUpdate(Long branchId) {
        BranchInfo branch = branchClient.getBranch(branchId);
        List<QueueEntry> active = queueEntryRepository.findActiveQueueForBranch(branchId, ACTIVE_STATUSES);
        long waiting = active.stream().filter(e -> e.getStatus() == QueueStatus.WAITING).count();
        int waitMinutes = calculateWaitTime(branch, (int) waiting);
        int capacityPct = branch.maxCapacity() > 0 ? (int) ((waiting * 100) / branch.maxCapacity()) : 0;

        QueueEntry called = active.stream()
                .filter(e -> e.getStatus() == QueueStatus.CALLED || e.getStatus() == QueueStatus.SERVING)
                .findFirst().orElse(null);

        QueueBroadcast broadcast = QueueBroadcast.builder()
                .branchId(branchId)
                .branchName(branch.name())
                .totalWaiting(waiting)
                .estimatedWaitMinutes(waitMinutes)
                .capacityPercent(Math.min(capacityPct, 100))
                .currentlyCalledTicket(called != null ? called.getTicketNumber() : null)
                .currentlyCalledCounter(called != null ? called.getCounterNumber() : null)
                .topEntries(active.stream().limit(10).map(e -> mapToResponse(e, branch)).toList())
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/branch/" + branchId + "/queue", broadcast);
        publishQueueCountChanged(branchId, active.size());
    }

    /**
     * Lets branch-service maintain a local event-fed cache of live queue
     * counts instead of polling this service synchronously for them (see
     * branch-service's QueueCountCache/QueueEventListener).
     */
    private void publishQueueCountChanged(Long branchId, long activeCount) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of("branchId", branchId, "activeCount", activeCount));
            rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE_EXCHANGE, "queue.count-changed", payload);
        } catch (Exception e) {
            log.warn("Failed to publish queue count change for branch {}: {}", branchId, e.getMessage());
        }
    }

    private int calculateWaitTime(BranchInfo branch, int queueSize) {
        if (branch.activeCounters() == null || branch.activeCounters() == 0) return queueSize * 8;
        return (queueSize * branch.avgServiceTimeMinutes()) / branch.activeCounters();
    }

    private String generateTicketNumber(Long branchId) {
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT);
        Optional<String> max = queueEntryRepository.findMaxTicketNumberForBranchToday(branchId, startOfDay);

        String prefix = "A";
        int nextNum = 1;

        if (max.isPresent()) {
            try {
                String lastTicket = max.get();
                prefix = lastTicket.substring(0, 1);
                nextNum = Integer.parseInt(lastTicket.substring(1)) + 1;
                if (nextNum > 999) {
                    prefix = String.valueOf((char) (prefix.charAt(0) + 1));
                    nextNum = 1;
                }
            } catch (Exception ignored) {}
        }

        return String.format("%s%03d", prefix, nextNum);
    }

    private QueueEntryResponse mapToResponse(QueueEntry entry, BranchInfo branch) {
        long total = queueEntryRepository.countByBranchIdAndStatusIn(entry.getBranchId(), ACTIVE_STATUSES);

        return QueueEntryResponse.builder()
                .id(entry.getId())
                .ticketNumber(entry.getTicketNumber())
                .serviceType(entry.getServiceType())
                .status(entry.getStatus().name())
                .positionInQueue(entry.getPositionInQueue())
                .estimatedWaitMinutes(entry.getEstimatedWaitMinutes())
                .counterNumber(entry.getCounterNumber())
                .qrCode(entry.getQrCode())
                .branchId(entry.getBranchId())
                .branchName(branch.name())
                .branchAddress(branch.address())
                .joinedAt(entry.getJoinedAt())
                .calledAt(entry.getCalledAt())
                .completedAt(entry.getCompletedAt())
                .totalInQueue(total)
                .build();
    }
}

package com.dha.queue.service;

import com.dha.queue.dto.request.QueueJoinRequest;
import com.dha.queue.dto.response.QueueBroadcast;
import com.dha.queue.dto.response.QueueEntryResponse;
import com.dha.queue.entity.Branch;
import com.dha.queue.entity.QueueEntry;
import com.dha.queue.entity.QueueEntry.QueueStatus;
import com.dha.queue.entity.QueueEntry.ServiceType;
import com.dha.queue.entity.User;
import com.dha.queue.repository.BranchRepository;
import com.dha.queue.repository.QueueEntryRepository;
import com.dha.queue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private static final List<QueueStatus> ACTIVE_STATUSES = List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.SERVING);

    @Transactional
    public QueueEntryResponse joinQueue(QueueJoinRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));

        // Check if user already in queue
        Optional<QueueEntry> existing = queueEntryRepository.findByUserIdAndStatusIn(user.getId(), ACTIVE_STATUSES);
        if (existing.isPresent()) {
            throw new IllegalStateException("You are already in a queue. Cancel your current ticket first.");
        }

        ServiceType serviceType;
        try {
            serviceType = ServiceType.valueOf(request.getServiceType());
        } catch (IllegalArgumentException e) {
            serviceType = ServiceType.GENERAL_ENQUIRY;
        }

        String ticketNumber = generateTicketNumber(branch.getId());
        long position = queueEntryRepository.countByBranchIdAndStatus(branch.getId(), QueueStatus.WAITING) + 1;
        int waitMinutes = calculateWaitTime(branch, (int) position);

        QueueEntry entry = QueueEntry.builder()
                .user(user)
                .branch(branch)
                .ticketNumber(ticketNumber)
                .serviceType(serviceType)
                .status(QueueStatus.WAITING)
                .positionInQueue((int) position)
                .estimatedWaitMinutes(waitMinutes)
                .qrCode("DHA-" + ticketNumber + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .build();

        entry = queueEntryRepository.save(entry);

        // Broadcast update to branch topic
        broadcastQueueUpdate(branch.getId());

        return mapToResponse(entry);
    }

    public QueueEntryResponse getMyTicket(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return queueEntryRepository.findByUserIdAndStatusIn(user.getId(), ACTIVE_STATUSES)
                .map(this::mapToResponse)
                .orElse(null);
    }

    @Transactional
    public void cancelTicket(Long entryId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));

        if (!entry.getUser().getId().equals(user.getId())) {
            throw new SecurityException("Not authorised to cancel this ticket");
        }

        entry.setStatus(QueueStatus.CANCELLED);
        queueEntryRepository.save(entry);

        broadcastQueueUpdate(entry.getBranch().getId());
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

        // Notify the specific user
        messagingTemplate.convertAndSend(
                "/topic/user/" + entry.getUser().getId() + "/status",
                mapToResponse(entry)
        );

        broadcastQueueUpdate(entry.getBranch().getId());
        return mapToResponse(entry);
    }

    public List<QueueEntryResponse> getBranchQueue(Long branchId) {
        return queueEntryRepository.findByBranchIdAndStatusInOrderByJoinedAtAsc(branchId, ACTIVE_STATUSES)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<QueueEntryResponse> getMyHistory(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return queueEntryRepository.findByUserIdOrderByJoinedAtDesc(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    public void broadcastQueueUpdate(Long branchId) {
        branchRepository.findById(branchId).ifPresent(branch -> {
            List<QueueEntry> active = queueEntryRepository.findActiveQueueForBranch(branchId, ACTIVE_STATUSES);
            long waiting = active.stream().filter(e -> e.getStatus() == QueueStatus.WAITING).count();
            int waitMinutes = calculateWaitTime(branch, (int) waiting);
            int capacityPct = branch.getMaxCapacity() > 0 ? (int) ((waiting * 100) / branch.getMaxCapacity()) : 0;

            QueueEntry called = active.stream()
                    .filter(e -> e.getStatus() == QueueStatus.CALLED || e.getStatus() == QueueStatus.SERVING)
                    .findFirst().orElse(null);

            QueueBroadcast broadcast = QueueBroadcast.builder()
                    .branchId(branchId)
                    .branchName(branch.getName())
                    .totalWaiting(waiting)
                    .estimatedWaitMinutes(waitMinutes)
                    .capacityPercent(Math.min(capacityPct, 100))
                    .currentlyCalledTicket(called != null ? called.getTicketNumber() : null)
                    .currentlyCalledCounter(called != null ? called.getCounterNumber() : null)
                    .topEntries(active.stream().limit(10).map(this::mapToResponse).toList())
                    .timestamp(LocalDateTime.now())
                    .build();

            messagingTemplate.convertAndSend("/topic/branch/" + branchId + "/queue", broadcast);
        });
    }

    private int calculateWaitTime(Branch branch, int queueSize) {
        if (branch.getActiveCounters() == null || branch.getActiveCounters() == 0) return queueSize * 8;
        return (queueSize * branch.getAvgServiceTimeMinutes()) / branch.getActiveCounters();
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

    private QueueEntryResponse mapToResponse(QueueEntry entry) {
        long total = queueEntryRepository.countByBranchIdAndStatusIn(
                entry.getBranch().getId(), ACTIVE_STATUSES);

        return QueueEntryResponse.builder()
                .id(entry.getId())
                .ticketNumber(entry.getTicketNumber())
                .serviceType(entry.getServiceType().name())
                .status(entry.getStatus().name())
                .positionInQueue(entry.getPositionInQueue())
                .estimatedWaitMinutes(entry.getEstimatedWaitMinutes())
                .counterNumber(entry.getCounterNumber())
                .qrCode(entry.getQrCode())
                .branchId(entry.getBranch().getId())
                .branchName(entry.getBranch().getName())
                .branchAddress(entry.getBranch().getAddress())
                .joinedAt(entry.getJoinedAt())
                .calledAt(entry.getCalledAt())
                .completedAt(entry.getCompletedAt())
                .totalInQueue(total)
                .build();
    }
}
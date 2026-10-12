package com.dha.queue.service;

import com.dha.queue.client.BranchClient.BranchInfo;
import com.dha.queue.entity.QueueEntry;
import com.dha.queue.entity.QueueEntry.QueueStatus;
import com.dha.queue.repository.QueueEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Ticket-number assignment (read max, +1, insert) is a check-then-act with
 * no atomic DB operation backing it, so two concurrent joins for the same
 * branch can compute the same "next" ticket number. uk_queue_entries_branch_date_ticket
 * (see QueueEntry) turns that race into a constraint violation instead of a
 * silent duplicate — this class exists only so that violation can be retried
 * in its own fresh transaction (REQUIRES_NEW), since retrying inside the
 * same transaction that just failed to flush doesn't work with JPA/Hibernate.
 */
@Service
@RequiredArgsConstructor
public class TicketClaimService {

    private final QueueEntryRepository queueEntryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public QueueEntry claimTicket(BranchInfo branch, UUID userId, String serviceType, int waitMinutes) {
        String ticketNumber = generateTicketNumber(branch.id());
        long position = queueEntryRepository.countByBranchIdAndStatus(branch.id(), QueueStatus.WAITING) + 1;

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

        // IDENTITY id generation forces an immediate INSERT here (not
        // deferred to flush/commit), so a unique-constraint violation throws
        // synchronously and this REQUIRES_NEW transaction rolls back cleanly
        // without touching whatever transaction the caller is in.
        return queueEntryRepository.save(entry);
    }

    private String generateTicketNumber(Long branchId) {
        Optional<String> max = queueEntryRepository.findMaxTicketNumberForBranchToday(branchId, LocalDate.now());

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
}

package com.dha.queue.service;

import com.dha.queue.client.BranchClient;
import com.dha.queue.client.BranchClient.BranchInfo;
import com.dha.queue.client.CatalogClient;
import com.dha.queue.client.DocumentClient;
import com.dha.queue.client.MlServiceClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueService {

    private final QueueEntryRepository queueEntryRepository;
    private final BranchClient branchClient;
    private final CatalogClient catalogClient;
    private final DocumentClient documentClient;
    private final MlServiceClient mlServiceClient;
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TicketClaimService ticketClaimService;
    private final QrCodeService qrCodeService;

    @Value("${app.queue.skip-document-check:false}")
    private boolean skipDocumentCheck;

    private static final List<QueueStatus> ACTIVE_STATUSES = List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.SERVING);

    private static final int MAX_TICKET_CLAIM_ATTEMPTS = 3;

    public QueueEntryResponse joinQueue(QueueJoinRequest request, UUID userId) {
        BranchInfo branch = branchClient.getBranch(request.getBranchId());

        // Check if user already in queue
        Optional<QueueEntry> existing = queueEntryRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
        if (existing.isPresent()) {
            throw new IllegalStateException("You are already in a queue. Cancel your current ticket first.");
        }

        requireDocumentsOnFile(request.getServiceType(), userId);

        long position = queueEntryRepository.countByBranchIdAndStatus(branch.id(), QueueStatus.WAITING) + 1;
        int waitMinutes = calculateWaitTime(branch, (int) position);

        // Two concurrent joins for the same branch can both compute the same
        // "next" ticket number (see TicketClaimService) — retry a few times
        // on the resulting unique-constraint violation rather than letting
        // one of the two joins fail outright.
        QueueEntry entry = null;
        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 0; attempt < MAX_TICKET_CLAIM_ATTEMPTS; attempt++) {
            try {
                entry = ticketClaimService.claimTicket(branch, userId, request.getServiceType(), waitMinutes);
                lastFailure = null;
                break;
            } catch (DataIntegrityViolationException e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }

        // Broadcast update to branch topic
        broadcastQueueUpdate(branch.id());

        return mapToResponse(entry, branch);
    }

    public QueueEntryResponse getMyTicket(UUID userId) {
        return queueEntryRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
                .map(entry -> mapToResponse(entry, branchClient.getBranch(entry.getBranchId())))
                .orElse(null);
    }

    // Lets feedback-service verify a queueEntryId actually belongs to the
    // citizen submitting feedback for it, rather than trusting the client's
    // own claim of which entry the feedback is about.
    public QueueEntryResponse getEntryForRequester(Long entryId, UUID requesterId, String requesterRole) {
        QueueEntry entry = queueEntryRepository.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Queue entry not found"));

        boolean isOwner = entry.getUserId().equals(requesterId);
        boolean isStaff = "STAFF".equals(requesterRole) || "ADMIN".equals(requesterRole);
        if (!isOwner && !isStaff) {
            throw new SecurityException("Not authorised to access this queue entry");
        }

        return mapToResponse(entry, branchClient.getBranch(entry.getBranchId()));
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
        List<QueueEntry> entries = queueEntryRepository.findByBranchIdAndStatusInOrderByJoinedAtAsc(branchId, ACTIVE_STATUSES);
        long total = queueEntryRepository.countByBranchIdAndStatusIn(branchId, ACTIVE_STATUSES);
        return entries.stream()
                // staff view: no per-entry QR image (only the citizen's own ticket shows it)
                .map(entry -> mapToResponse(entry, branch, total, false))
                .toList();
    }

    public List<QueueEntryResponse> getMyHistory(UUID userId) {
        Map<Long, BranchInfo> branchCache = new HashMap<>();
        Map<Long, Long> totalCache = new HashMap<>();
        return queueEntryRepository.findByUserIdOrderByJoinedAtDesc(userId)
                .stream()
                .map(entry -> mapToResponse(entry,
                        branchCache.computeIfAbsent(entry.getBranchId(), branchClient::getBranch),
                        totalCache.computeIfAbsent(entry.getBranchId(),
                                id -> queueEntryRepository.countByBranchIdAndStatusIn(id, ACTIVE_STATUSES))))
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
                // qrCode/qrCodeImage are meant to be private to the ticket holder
                // (it's what identifies/calls up that specific person) — strip
                // them before broadcasting to every citizen subscribed to this
                // branch's topic. active already is every ACTIVE_STATUSES entry
                // for this branch, so its size is the total-in-queue count — no
                // need for mapToResponse to run a separate COUNT query per entry.
                .topEntries(active.stream().limit(10)
                        .map(e -> mapToResponse(e, branch, active.size(), false))
                        .peek(r -> { r.setQrCode(null); r.setQrCodeImage(null); })
                        .toList())
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

    // Mirrors DocumentsPage.tsx's DOC_TYPE_ALIASES on the frontend — legacy
    // uploaded-document rows predate the fuller catalog category list, so a
    // document typed e.g. SA_ID must still satisfy a catalog requirement
    // asking for SA_SMART_ID. The frontend already treats these as
    // equivalent when showing a document as reusable; this check must agree,
    // or a citizen can see "document ready" and still be rejected at booking.
    private static final Map<String, Set<String>> DOC_TYPE_ALIASES = Map.of(
            "SA_SMART_ID",            Set.of("SA_SMART_ID", "SA_ID"),
            "GREEN_BARCODED_ID_BOOK", Set.of("GREEN_BARCODED_ID_BOOK", "SA_ID"),
            "FOREIGN_PASSPORT",       Set.of("FOREIGN_PASSPORT", "PASSPORT"),
            "UTILITY_BILL",           Set.of("UTILITY_BILL", "PROOF_OF_RESIDENCE")
    );

    // Services with no required documents at all (e.g. GENERAL_ENQUIRY,
    // walk-in-only services) skip the document-service round trip entirely —
    // there's nothing to check.
    private void requireDocumentsOnFile(String serviceType, UUID userId) {
        if (skipDocumentCheck) {
            log.debug("Document check BYPASSED userId={} serviceType={} reason=QUEUE_SKIP_DOCUMENT_CHECK enabled",
                    userId, serviceType);
            return;
        }

        List<CatalogClient.DocumentRequirement> requiredDocs = catalogClient.getRequiredDocuments(serviceType).stream()
                .filter(CatalogClient.DocumentRequirement::required)
                .toList();
        if (requiredDocs.isEmpty()) {
            log.debug("Document check SKIPPED userId={} serviceType={} reason=no required documents for this service",
                    userId, serviceType);
            return;
        }

        Set<String> validated = documentClient.getValidatedCategories(userId);
        List<String> missing = requiredDocs.stream()
                .filter(r -> DOC_TYPE_ALIASES.getOrDefault(r.documentCategory(), Set.of(r.documentCategory()))
                        .stream().noneMatch(validated::contains))
                .map(CatalogClient.DocumentRequirement::label)
                .toList();

        if (!missing.isEmpty()) {
            log.warn("Document check FAILED userId={} serviceType={} reason=missing [{}]",
                    userId, serviceType, String.join(", ", missing));
            throw new IllegalArgumentException(
                    "Missing required documents for this service: " + String.join(", ", missing)
                            + ". Please upload and complete verification for these before booking.");
        }
        log.info("Document check PASSED userId={} serviceType={}", userId, serviceType);
    }

    private int calculateWaitTime(BranchInfo branch, int queueSize) {
        LocalDateTime now = LocalDateTime.now();
        int arrivalsLast60Min = (int) queueEntryRepository.countByBranchIdAndJoinedAtBetween(
                branch.id(), now.minusMinutes(60), now);

        Optional<Integer> mlEstimate = mlServiceClient.predictWaitTime(queueSize, arrivalsLast60Min);
        if (mlEstimate.isPresent()) {
            return mlEstimate.get();
        }

        if (branch.activeCounters() == null || branch.activeCounters() == 0) return queueSize * 8;
        return (queueSize * branch.avgServiceTimeMinutes()) / branch.activeCounters();
    }

    private QueueEntryResponse mapToResponse(QueueEntry entry, BranchInfo branch) {
        long total = queueEntryRepository.countByBranchIdAndStatusIn(entry.getBranchId(), ACTIVE_STATUSES);
        return mapToResponse(entry, branch, total);
    }

    // Callers mapping a whole list for a known branch (or with the count
    // already in hand) pass totalInQueue directly instead of this method
    // re-querying it once per entry — mapping N entries for the same branch
    // previously ran N identical COUNT queries.
    private QueueEntryResponse mapToResponse(QueueEntry entry, BranchInfo branch, long totalInQueue) {
        return mapToResponse(entry, branch, totalInQueue, true);
    }

    // withQrImage=false skips rendering the QR PNG: the staff branch list and
    // the branch broadcast never show it, and rendering one per entry on every
    // poll/broadcast was what exhausted queue-service's memory under load.
    private QueueEntryResponse mapToResponse(QueueEntry entry, BranchInfo branch, long totalInQueue,
                                             boolean withQrImage) {
        return QueueEntryResponse.builder()
                .id(entry.getId())
                .ticketNumber(entry.getTicketNumber())
                .serviceType(entry.getServiceType())
                .status(entry.getStatus().name())
                .positionInQueue(entry.getPositionInQueue())
                .estimatedWaitMinutes(entry.getEstimatedWaitMinutes())
                .counterNumber(entry.getCounterNumber())
                .qrCode(entry.getQrCode())
                .qrCodeImage(withQrImage ? qrCodeService.generateDataUri(entry.getQrCode()) : null)
                .branchId(entry.getBranchId())
                .branchName(branch.name())
                .branchAddress(branch.address())
                .joinedAt(entry.getJoinedAt())
                .calledAt(entry.getCalledAt())
                .completedAt(entry.getCompletedAt())
                .totalInQueue(totalInQueue)
                .build();
    }
}

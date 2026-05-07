package com.dha.queue.repository;

import com.dha.queue.entity.QueueEntry;
import com.dha.queue.entity.QueueEntry.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    List<QueueEntry> findByBranchIdAndStatusInOrderByJoinedAtAsc(
            Long branchId, List<QueueStatus> statuses
    );

    Optional<QueueEntry> findByUserIdAndStatusIn(Long userId, List<QueueStatus> statuses);

    long countByBranchIdAndStatus(Long branchId, QueueStatus status);

    long countByBranchIdAndStatusIn(Long branchId, List<QueueStatus> statuses);

    @Query("SELECT MAX(q.ticketNumber) FROM QueueEntry q WHERE q.branch.id = :branchId AND q.joinedAt >= :since")
    Optional<String> findMaxTicketNumberForBranchToday(
            @Param("branchId") Long branchId,
            @Param("since") LocalDateTime since
    );

    List<QueueEntry> findByUserIdOrderByJoinedAtDesc(Long userId);

    @Query("SELECT q FROM QueueEntry q WHERE q.branch.id = :branchId AND q.status IN :statuses ORDER BY q.joinedAt ASC")
    List<QueueEntry> findActiveQueueForBranch(
            @Param("branchId") Long branchId,
            @Param("statuses") List<QueueStatus> statuses
    );
}
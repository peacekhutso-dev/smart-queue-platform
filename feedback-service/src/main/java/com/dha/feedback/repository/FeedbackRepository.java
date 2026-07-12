package com.dha.feedback.repository;

import com.dha.feedback.entity.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByBranchIdOrderBySubmittedAtDesc(Long branchId);
    List<Feedback> findByUserIdOrderBySubmittedAtDesc(UUID userId);
}
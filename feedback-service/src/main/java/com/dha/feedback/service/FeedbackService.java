package com.dha.feedback.service;

import com.dha.feedback.client.BranchClient;
import com.dha.feedback.client.QueueClient;
import com.dha.feedback.dto.request.FeedbackRequest;
import com.dha.feedback.entity.Feedback;
import com.dha.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final BranchClient branchClient;
    private final QueueClient queueClient;

    @Transactional
    public void submitFeedback(FeedbackRequest request, UUID userId, String userRole) {
        var branch = branchClient.getBranch(request.getBranchId());

        Long entryBranchId = queueClient.getEntryBranchId(request.getQueueEntryId(), userId, userRole);
        if (!entryBranchId.equals(branch.id())) {
            throw new SecurityException("Queue entry does not belong to the specified branch");
        }

        Feedback feedback = Feedback.builder()
                .userId(userId)
                .branchId(branch.id())
                .queueEntryId(request.getQueueEntryId())
                .overallRating(request.getOverallRating())
                .staffHelpful(request.getStaffHelpful())
                .emotionRating(request.getEmotionRating())
                .comment(request.getComment())
                .serviceType(request.getServiceType())
                .build();

        feedbackRepository.save(feedback);
    }
}

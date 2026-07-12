package com.dha.feedback.service;

import com.dha.feedback.client.BranchClient;
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

    @Transactional
    public void submitFeedback(FeedbackRequest request, UUID userId) {
        var branch = branchClient.getBranch(request.getBranchId());

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

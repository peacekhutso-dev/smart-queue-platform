package com.dha.queue.service;

import com.dha.queue.dto.request.FeedbackRequest;
import com.dha.queue.entity.Branch;
import com.dha.queue.entity.Feedback;
import com.dha.queue.entity.User;
import com.dha.queue.repository.BranchRepository;
import com.dha.queue.repository.FeedbackRepository;
import com.dha.queue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;

    @Transactional
    public void submitFeedback(FeedbackRequest request, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new IllegalArgumentException("Branch not found"));

        Feedback feedback = Feedback.builder()
                .user(user)
                .branch(branch)
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
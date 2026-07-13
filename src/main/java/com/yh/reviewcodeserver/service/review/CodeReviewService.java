package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.dto.ReviewHistoryDto;
import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.dto.ReviewResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CodeReviewService {
    ReviewResult review(ReviewRequest request);

    void saveSuccessReview(ReviewRequest reviewRequest, ReviewResult reviewResult);

    Page<ReviewHistoryDto> getReviewHistories(Pageable pageable);

    Page<ReviewHistoryDto> getReviewHistoriesByRepository(Pageable pageable, String repository);

    ReviewHistoryDto getReviewHistory(Long id);
}

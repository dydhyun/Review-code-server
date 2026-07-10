package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.dto.ReviewResult;

public interface CodeReviewService {
    ReviewResult review(ReviewRequest request);

    void saveSuccessReview(ReviewRequest reviewRequest, ReviewResult reviewResult);

}

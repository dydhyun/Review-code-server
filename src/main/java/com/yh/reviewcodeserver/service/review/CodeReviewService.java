package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.dto.ReviewRequest;

public interface CodeReviewService {
    String review(ReviewRequest request);

    void saveSuccessReview();

}

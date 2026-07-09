package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;

public interface CodeReviewService {
    String review(ReviewRequest request);
}

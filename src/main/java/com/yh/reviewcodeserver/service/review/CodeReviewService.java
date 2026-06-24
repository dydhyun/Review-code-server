package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;

public interface CodeReviewService {
    public void review(ReviewRequest request);
}

package com.yh.reviewcodeserver.controller.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.service.review.CodeReviewService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final CodeReviewService codeReviewService;

    public ReviewController(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @PostMapping("")
    public void review(@RequestBody ReviewRequest request){
        codeReviewService.review(request);
    }
}

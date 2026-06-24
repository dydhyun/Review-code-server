package com.yh.reviewcodeserver.controller.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.service.review.CodeReviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.security.sasl.AuthenticationException;


@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final CodeReviewService codeReviewService;

    @Value("${action.review.api.key}")
    private String reviewApiKey;


    public ReviewController(CodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @PostMapping("")
    public void review(@RequestBody ReviewRequest request,
                       @RequestHeader("Authorization") String authorization){

        if (!(("Bearer " + reviewApiKey).equals(authorization))){

            throw new RuntimeException("인증되지 않은 접근입니다.");

        }

        codeReviewService.review(request);
    }
}

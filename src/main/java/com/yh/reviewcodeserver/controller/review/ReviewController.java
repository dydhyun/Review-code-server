package com.yh.reviewcodeserver.controller.review;

import com.yh.reviewcodeserver.dto.ReviewHistoryDto;
import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.queue.model.DlqItem;
import com.yh.reviewcodeserver.queue.service.DlqService;
import com.yh.reviewcodeserver.queue.service.RedisQueueService;
import com.yh.reviewcodeserver.service.review.CodeReviewService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final RedisQueueService redisQueueService;
    private final DlqService dlqService;
    private final CodeReviewService codeReviewService;

    public ReviewController(RedisQueueService redisQueueService, DlqService dlqService, CodeReviewService codeReviewService) {
        this.redisQueueService = redisQueueService;
        this.dlqService = dlqService;
        this.codeReviewService = codeReviewService;
    }


    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping
    public void requestReview(@RequestBody ReviewRequest request){
        redisQueueService.enqueue(request);
    }

    @GetMapping
    public Page<ReviewHistoryDto> getReviewHistories(
            @RequestParam(required = false) String repository,
            @PageableDefault Pageable pageable){

        if (repository != null && !repository.isBlank()) {
            return codeReviewService.getReviewHistoriesByRepository(repository, pageable);
        }
        return codeReviewService.getReviewHistories(pageable);
    }

    @GetMapping("/{id}")
    public ReviewHistoryDto getReviewHistory(@PathVariable Long id){
        return codeReviewService.getReviewHistory(id);
    }

    @GetMapping("/failed")
    public Page<DlqItem> getFailedReviews(@PageableDefault Pageable pageable){
        return dlqService.getFailedReviews(pageable);
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{recordId}/retry")
    public void retryReview(@PathVariable String recordId){
        dlqService.retryReview(recordId);
    }

}

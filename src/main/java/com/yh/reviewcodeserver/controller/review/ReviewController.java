package com.yh.reviewcodeserver.controller.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.queue.model.DlqItem;
import com.yh.reviewcodeserver.queue.service.DlqService;
import com.yh.reviewcodeserver.queue.service.RedisQueueService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final RedisQueueService redisQueueService;
    private final DlqService dlqService;

    public ReviewController(RedisQueueService redisQueueService, DlqService dlqService) {
        this.redisQueueService = redisQueueService;
        this.dlqService = dlqService;
    }


    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("")
    public void review(@RequestBody ReviewRequest request){
        redisQueueService.enqueue(request);
    }

    @GetMapping("/failed")
    public List<DlqItem> getFailedReview(
            @RequestParam(defaultValue = "0") int startIndex,
            @RequestParam(defaultValue = "10") int count){
        List<DlqItem> failedReviews = dlqService.getFailedReviews(startIndex, count);
        return failedReviews;
    }

    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("/{recordId}/retry")
    public void retryReview(@PathVariable int recordId){
        dlqService.retryReview(recordId);
    }

}

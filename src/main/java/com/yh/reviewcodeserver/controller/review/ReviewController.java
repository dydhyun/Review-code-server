package com.yh.reviewcodeserver.controller.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.queue.service.RedisQueueService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final RedisQueueService redisQueueService;

    public ReviewController(RedisQueueService redisQueueService) {
        this.redisQueueService = redisQueueService;
    }


    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping("")
    public void review(@RequestBody ReviewRequest request){
        redisQueueService.enqueue(request);
    }
}

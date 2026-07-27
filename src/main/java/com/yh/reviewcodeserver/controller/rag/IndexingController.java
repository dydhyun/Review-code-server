package com.yh.reviewcodeserver.controller.rag;

import com.yh.reviewcodeserver.dto.IndexingRequest;
import com.yh.reviewcodeserver.queue.service.IndexingQueueService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/index")
public class IndexingController {

    private final IndexingQueueService indexingQueueService;

    public IndexingController(IndexingQueueService indexingQueueService) {
        this.indexingQueueService = indexingQueueService;
    }

    @PostMapping
    public void requestIndexing(@RequestBody IndexingRequest indexingRequest){
        indexingQueueService.enqueue(indexingRequest);
    }
}

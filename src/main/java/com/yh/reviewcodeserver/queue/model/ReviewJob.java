package com.yh.reviewcodeserver.queue.model;

import com.yh.reviewcodeserver.dto.ReviewRequest;

// Worker가 ReviewService를 호출하기 위해 필요한 최소 정보. (actions에서 ReviewService로 넘기는 정보.)
// 추후 runId, retryCount, 생성 시간 과 같은 필드를 추가할 예정
public record ReviewJob (
        ReviewRequest request
){}

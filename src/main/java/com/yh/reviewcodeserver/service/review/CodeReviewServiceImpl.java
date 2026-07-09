package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.client.llm.openrouter.OpenRouterClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewServiceImpl implements CodeReviewService{

    private final OpenRouterClient openRouterClient;

    @Override
    public String review(ReviewRequest request) {

        if (request.diff() == null || request.diff().isBlank()) {
            log.info("{} repo 커밋 발생. 검토사항 없음.", request.repository());
            return request.getCommitInfo() + "검토할 코드 변경사항이 없습니다.";
        }

        String reviewResult = openRouterClient.review(request);
        return  reviewResult;
    }

}
package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.client.llm.openrouter.OpenRouterClient;
import com.yh.reviewcodeserver.service.slack.SlackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewServiceImpl implements CodeReviewService{

    private final OpenRouterClient openRouterClient;
    private final SlackService slackService;

    @Override
    public void review(ReviewRequest request) {


        if (request.diff() == null || request.diff().isBlank()) {
            log.info("{} repo 커밋 발생. 검토사항 없음.", request.repository());
            return;
        }

        String reviewResult = openRouterClient.review(request);

        slackService.sendMessage(reviewResult);
    }
}

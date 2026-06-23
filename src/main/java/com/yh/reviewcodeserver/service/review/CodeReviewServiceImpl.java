package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.client.llm.openrouter.OpenRouterClient;
import com.yh.reviewcodeserver.service.slack.SlackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodeReviewServiceImpl implements CodeReviewService{

    private final OpenRouterClient openRouterClient;
    private final SlackService slackService;

    @Override
    public void review(String diff) {

        String reviewResult = openRouterClient.review(diff);

        slackService.sendMessage(reviewResult);
    }
}

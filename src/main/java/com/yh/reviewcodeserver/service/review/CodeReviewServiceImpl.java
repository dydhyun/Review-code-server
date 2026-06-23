package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.client.openai.OpenAiClient;
import com.yh.reviewcodeserver.service.slack.SlackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CodeReviewServiceImpl implements CodeReviewService{

    private final OpenAiClient openAiClient;
    private final SlackService slackService;

    @Override
    public void review(String diff) {

        String reviewResult =
                openAiClient.review(diff);

        slackService.sendMessage(reviewResult);
    }
}

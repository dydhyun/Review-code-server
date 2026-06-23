package com.yh.reviewcodeserver.client.slack;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SlackClient {

    private final RestTemplate restTemplate;
    private final SlackProperties slackProperties;

    public void sendMessage(String message) {

        Map<String, String> body = Map.of(
                "text",
                message
        );

        restTemplate.postForObject(
                slackProperties.getWebhookUrl(),
                body,
                String.class
        );
    }

}

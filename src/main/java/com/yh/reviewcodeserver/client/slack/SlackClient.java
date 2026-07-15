package com.yh.reviewcodeserver.client.slack;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SlackClient {

    private static final Logger log = LoggerFactory.getLogger(SlackClient.class);
    private final RestTemplate restTemplate;
    private final SlackProperties slackProperties;

    public void sendMessage(String message) {
        send(message, slackProperties.getWebhookUrl());
    }

    public void sendMessage(String message, String repository) {
        String webhookUrl = resolveWebhookUrl(repository);
        send(message, webhookUrl);
    }

    private String resolveWebhookUrl(String repository) {
        String webhookUrl = slackProperties.getWebhooks().get(repository);

        if (webhookUrl == null) {
            log.warn("Repository[{}]에 등록된 Slack webhook이 없어 기본 채널로 전송합니다.", repository);
            return slackProperties.getWebhookUrl();
        }

        return webhookUrl;
    }

    private void send(String message, String webhookUrl) {
        Map<String, String> body = Map.of("text", message);
        restTemplate.postForObject(webhookUrl, body, String.class);
    }

}

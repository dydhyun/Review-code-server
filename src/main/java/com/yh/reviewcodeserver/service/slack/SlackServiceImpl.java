package com.yh.reviewcodeserver.service.slack;

import com.yh.reviewcodeserver.client.slack.SlackClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SlackServiceImpl implements SlackService{

    private final SlackClient slackClient;

    public SlackServiceImpl(SlackClient slackClient) {
        this.slackClient = slackClient;
    }

    @Override
    public void sendMessage(String message) {

        slackClient.sendMessage(message);
    }

    @Override
    public void sendMessage(String message, String repository) {

        slackClient.sendMessage(message, repository);
    }

}

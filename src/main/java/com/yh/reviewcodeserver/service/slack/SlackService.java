package com.yh.reviewcodeserver.service.slack;

public interface SlackService {
    void sendMessage(String message);

    void sendMessage(String message, String repository);
}

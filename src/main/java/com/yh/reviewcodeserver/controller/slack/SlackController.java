package com.yh.reviewcodeserver.controller.slack;

import com.yh.reviewcodeserver.service.slack.SlackService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController()
@RequestMapping("/slack")
public class SlackController {
    private final SlackService slackService;

    public SlackController(SlackService slackService) {
        this.slackService = slackService;
    }

    @PostMapping("/message/v1")
    public void sendToSlack(@RequestBody String message){
        System.out.println("test2");
        slackService.sendMessage(message);
    }
}
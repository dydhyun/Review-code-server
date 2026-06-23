package com.yh.reviewcodeserver.client.slack;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "slack")
public class SlackProperties {

    private String webhookUrl;
}

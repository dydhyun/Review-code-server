package com.yh.reviewcodeserver.config.webhook;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@ConfigurationProperties(prefix = "slack")
@Component
public class SlackProperties {

    private String webhookUrl;

}

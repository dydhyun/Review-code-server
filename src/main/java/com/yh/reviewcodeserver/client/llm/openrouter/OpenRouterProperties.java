package com.yh.reviewcodeserver.client.llm.openrouter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openrouter")
public class OpenRouterProperties {

    private String apiKey;
}
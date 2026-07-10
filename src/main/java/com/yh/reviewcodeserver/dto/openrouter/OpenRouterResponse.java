package com.yh.reviewcodeserver.dto.openrouter;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OpenRouterResponse {

    private List<Choice> choices;
    private Usage usage;

    @Getter
    @Setter
    public static class Choice {
        private OpenRouterMessage message;
    }

    @Getter
    @Setter
    public static class OpenRouterMessage {
        private String content;
    }

    @Getter
    @Setter
    public static class Usage {

        private int prompt_tokens;
        private int completion_tokens;
        private int total_tokens;
    }
}
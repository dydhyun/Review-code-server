package com.yh.reviewcodeserver.dto;

public record ReviewResult(
        String contents,
        int promptTokens,
        int completionTokens
) {
    public static ReviewResult withoutUsage(String content) {
        return new ReviewResult(content, 0, 0);
    }

    public boolean hasUsage() {
        return promptTokens > 0;
    }

}

package com.yh.reviewcodeserver.dto;

public record OpenAiRequest(
        String model,
        String input
) {
}
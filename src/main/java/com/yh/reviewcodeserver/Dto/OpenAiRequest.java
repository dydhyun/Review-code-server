package com.yh.reviewcodeserver.Dto;

public record OpenAiRequest(
        String model,
        String input
) {
}
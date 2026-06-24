package com.yh.reviewcodeserver.Dto;

public record ReviewRequest(
        String diff,
        String author,
        String repository,
        String commitMessage
) {
}
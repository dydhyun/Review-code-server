package com.yh.reviewcodeserver.Dto;

public record ReviewRequest(
        String diff,
        String author,
        String repository,
        String commitMessage,
        String runId
) {
    public int getDiffSize() {
        if (diff == null || diff.isBlank()) {
            return 0;
        }
        return diff.split("\n", -1).length;
    }
}
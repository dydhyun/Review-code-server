package com.yh.reviewcodeserver.queue.model;

public record DlqItem (
        String recordId,
        String payload
) {}

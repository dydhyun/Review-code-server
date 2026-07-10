package com.yh.reviewcodeserver.dto;

import com.yh.reviewcodeserver.entity.ReviewHistoryEntity;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReviewHistoryDto {

    private Long id;
    private String author;
    private String repository;
    private String commitId;
    private String review;
    private Integer promptTokens;
    private Integer completionTokens;
    private Double cost;
    private LocalDateTime createdAt;

    public static ReviewHistoryDto from(ReviewHistoryEntity entity) {
        return ReviewHistoryDto.builder()
                .id(entity.getId())
                .author(entity.getAuthor())
                .repository(entity.getRepository())
                .commitId(entity.getCommitId())
                .review(entity.getReview())
                .promptTokens(entity.getPromptTokens())
                .completionTokens(entity.getCompletionTokens())
                .cost(entity.getCost())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
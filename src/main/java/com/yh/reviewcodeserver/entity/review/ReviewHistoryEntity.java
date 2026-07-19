package com.yh.reviewcodeserver.entity.review;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewHistoryEntity {

    @Id
    @GeneratedValue
    private Long id;

    private String author;
    private String repository;
    private String commitId;

    @Column(columnDefinition = "TEXT")
    private String review;

    private Integer promptTokens;
    private Integer completionTokens;
    private Double cost;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public ReviewHistoryEntity(String author, String repository, String commitId,
                               String review, Integer promptTokens,
                               Integer completionTokens, Double cost) {
        this.author = author;
        this.repository = repository;
        this.commitId = commitId;
        this.review = review;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.cost = cost;
    }
}
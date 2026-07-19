package com.yh.reviewcodeserver.service.review;

import com.yh.reviewcodeserver.dto.ReviewHistoryDto;
import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.client.llm.openrouter.OpenRouterClient;
import com.yh.reviewcodeserver.dto.ReviewResult;
import com.yh.reviewcodeserver.entity.review.ReviewHistoryEntity;
import com.yh.reviewcodeserver.repository.review.ReviewHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeReviewServiceImpl implements CodeReviewService{

    private final OpenRouterClient openRouterClient;
    private final ReviewHistoryRepository reviewHistoryRepository;

    @Override
    public ReviewResult review(ReviewRequest request) {

        if (request.diff() == null || request.diff().isBlank()) {
            log.info("{} repo 커밋 발생. 검토사항 없음.", request.repository());
            return ReviewResult.withoutUsage(request.getCommitInfo() + "검토할 코드 변경사항이 없습니다.");
        }

        return openRouterClient.review(request);
    }

    @Override
    public void saveSuccessReview(ReviewRequest reviewRequest, ReviewResult reviewResult) {
        ReviewHistoryEntity reviewHistory = ReviewHistoryEntity.builder()
                .author(reviewRequest.author())
                .repository(reviewRequest.repository())
                .commitId(reviewRequest.commitId())
                .review(reviewResult.contents())
                .promptTokens(reviewResult.promptTokens())
                .completionTokens(reviewResult.completionTokens())
                .cost(0.0)
                .build();

        reviewHistoryRepository.save(reviewHistory);
    }

    @Override
    public Page<ReviewHistoryDto> getReviewHistories(Pageable pageable) {
        return reviewHistoryRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(ReviewHistoryDto::from);
    }

    @Override
    public Page<ReviewHistoryDto> getReviewHistoriesByRepository(String repository, Pageable pageable) {
        return reviewHistoryRepository.findByRepositoryOrderByCreatedAtDesc(repository, pageable);
    }

    @Override
    public ReviewHistoryDto getReviewHistory(Long id) {
        ReviewHistoryEntity history = reviewHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 리뷰 이력을 찾을 수 없습니다."));
        return ReviewHistoryDto.from(history);
    }
}
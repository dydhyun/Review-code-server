package com.yh.reviewcodeserver.repository.review;

import com.yh.reviewcodeserver.dto.ReviewHistoryDto;
import com.yh.reviewcodeserver.entity.review.ReviewHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewHistoryRepository extends JpaRepository<ReviewHistoryEntity, Long> {

    Page<ReviewHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ReviewHistoryDto> findByRepositoryOrderByCreatedAtDesc(String repository, Pageable pageable);
}

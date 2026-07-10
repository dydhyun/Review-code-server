package com.yh.reviewcodeserver.repository.review;

import com.yh.reviewcodeserver.entity.ReviewHistoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewHistoryRepository extends JpaRepository<ReviewHistoryEntity, Long> {

    Page<ReviewHistoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

}

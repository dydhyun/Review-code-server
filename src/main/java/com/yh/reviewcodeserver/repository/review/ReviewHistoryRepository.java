package com.yh.reviewcodeserver.repository.review;

import com.yh.reviewcodeserver.entity.ReviewHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewHistoryRepository extends JpaRepository<ReviewHistoryEntity, Long> {

    void saveReview();

}

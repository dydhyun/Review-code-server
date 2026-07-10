package com.yh.reviewcodeserver.queue.service;

import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.dto.ReviewResult;
import com.yh.reviewcodeserver.queue.model.DlqItem;
import com.yh.reviewcodeserver.queue.model.ReviewJob;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import com.yh.reviewcodeserver.service.review.CodeReviewService;
import com.yh.reviewcodeserver.service.slack.SlackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DlqService {

    private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(30);
    private static final Logger log = LoggerFactory.getLogger(DlqService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CodeReviewService codeReviewService;
    private final SlackService slackService;

    public List<DlqItem> getFailedReviews(int startIndex, int count){

        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                .range(StreamNames.DLQ, Range.unbounded());

        if (records == null || records.isEmpty()){
            return List.of();
        }
        return records.stream()
                .skip(startIndex)
                .limit(count)
                .map(record -> new DlqItem(
                        record.getId().getValue(),
                        (String) record.getValue().get("payload"))).toList();
    }

    public void retryReview(String recordId) {

        String lockKey = "dlq-processing:" + recordId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey,"1", LOCK_TIMEOUT);
        // 키가 존재한다면 false 저장
        if (!Boolean.TRUE.equals(acquired)) {
            throw new IllegalStateException("이미 처리중인 DLQ 항목입니다:" + recordId);
        }

        try {
            String payload = findPayload(recordId);
            ReviewJob job = objectMapper.readValue(payload, ReviewJob.class);
            ReviewRequest reviewRequest = job.request();

            ReviewResult result = codeReviewService.review(reviewRequest);
            if (result.hasUsage()){
                slackService.sendMessage("🔄[DLQ 복구 성공] " + reviewRequest.repository() + "\n" + result.contents());
                redisTemplate.opsForStream().delete(StreamNames.DLQ, RecordId.of(recordId));
                codeReviewService.saveSuccessReview(reviewRequest, result);
                log.info("DLQ 수동 재처리 성공, 삭제 완료: {}", recordId);
            } else {
                log.info("DLQ 재처리 실패: {}", recordId);
                slackService.sendMessage("⚠ DLQ 재처리 실패: " + recordId + "\n" + result.contents());
            }

        } catch (Exception e){
            log.error("DLQ 수동 재처리 실패: {}", recordId, e);
            slackService.sendMessage("⚠ DLQ 수동 재처리 실패: " + recordId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private String findPayload(String recordId) {

        List<MapRecord<String, Object, Object>> found = redisTemplate.opsForStream()
                .range(StreamNames.DLQ, Range.closed(recordId, recordId));

        if (found.isEmpty()){
            throw new IllegalArgumentException("DLQ에서 해당 항목을 찾을 수 없습니다:" + recordId);
        }
        return (String) found.get(0).getValue().get("payload");
    }

}

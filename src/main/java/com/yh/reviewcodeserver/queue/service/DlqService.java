package com.yh.reviewcodeserver.queue.service;

import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.dto.ReviewResult;
import com.yh.reviewcodeserver.queue.model.DlqItem;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import com.yh.reviewcodeserver.queue.worker.ReviewStreamInitializer;
import com.yh.reviewcodeserver.service.review.CodeReviewService;
import com.yh.reviewcodeserver.service.slack.SlackService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DlqService {

    private static final int MAX_RETRY = 5;
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
            ReviewRequest reviewRequest = objectMapper.readValue(payload, ReviewRequest.class);

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
            slackService.sendMessage("⚠ DLQ 수동 재처리 실패: " + recordId + "\n원인: " + e.getMessage());
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



    // reclaimAndRetry에서 호출: ID만 있으니 내용을 조회해서 옮김
    public void moveToDlq(RecordId id, long attemptNumber) {
        List<MapRecord<String, Object, Object>> found = redisTemplate.opsForStream().range(
                StreamNames.REVIEW,
                Range.closed(id.getValue(), id.getValue())
        );

        if (found.isEmpty()) {
            log.warn("DLQ 이동 대상 메세지를 찾을 수 없음: {}", id);
            ack(id);
            return;
        }

        moveToDlq(found.get(0), attemptNumber);
    }

    // handleRecord에서 호출: 이미 내용을 갖고 있으니 바로 사용
    private void moveToDlq(MapRecord<String, Object, Object> record, long attemptNumber) {
        String payload = (String) record.getValue().get("payload");

        Map<String, String> dlqBody = Map.of(
                "payload", payload,
                "originalId", record.getId().getValue(),
                "failedAttemptCount", String.valueOf(attemptNumber)
        );

        redisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(StreamNames.DLQ)
                        .ofMap(dlqBody)
        );

        ack(record.getId()); // 원본 스트림에서는 제거
        log.info("DLQ로 이동 완료: {} → {}", record.getId(), StreamNames.DLQ);

        notifyDlqFailure(payload);
    }

    private void ack(RecordId id) {
        redisTemplate.opsForStream()
                .acknowledge(StreamNames.REVIEW, ReviewStreamInitializer.GROUP_NAME, id);
    }

    private void notifyDlqFailure(String payload) {
        try {
            ReviewRequest reviewRequest = objectMapper.readValue(payload, ReviewRequest.class);
            String repository = reviewRequest.repository();
            slackService.sendMessage("⚠ [ " + repository + " ]" + " 리뷰 생성에 실패했습니다. 재시도 " + MAX_RETRY + " 회 초과.");
        }catch (Exception e){
            log.error("DLQ 실패 알림 전송 중 오류", e);
            slackService.sendMessage("⚠ 리뷰 생성에 실패했습니다.");
        }
    }

}

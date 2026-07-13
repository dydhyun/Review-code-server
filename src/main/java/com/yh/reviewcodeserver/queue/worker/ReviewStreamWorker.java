package com.yh.reviewcodeserver.queue.worker;

import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.dto.ReviewResult;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import com.yh.reviewcodeserver.queue.service.DlqService;
import com.yh.reviewcodeserver.service.review.CodeReviewService;
import com.yh.reviewcodeserver.service.slack.SlackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewStreamWorker {

    private static final String CONSUMER_NAME = "review-worker-1";
    private static final int MAX_RETRY = 5;
    private static final Duration RECLAIM_IDLE_TIME = Duration.ofSeconds(30);
    // api 호출하고 응답받아서 ack 처리 하는 보호시간

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CodeReviewService codeReviewService;
    private final SlackService slackService;
    private final DlqService dlqService;

    // 직접구현 이후 Spring Data Redis의 StreamMessageListenerContainer로 변경
    @Scheduled(fixedRate = 1000)
    public void poll(){

        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                Consumer.from(ReviewStreamInitializer.GROUP_NAME, CONSUMER_NAME),
                StreamReadOptions.empty().count(10),
                StreamOffset.create(StreamNames.REVIEW, ReadOffset.lastConsumed())
        );
        if (records.isEmpty()){
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            log.info("메세지 수신 : {}", record);
            handleRecord(record, 0);
        }

    }

    @Scheduled(fixedRate = 10000)
    public void reclaimAndRetry() {

        PendingMessages pending = stringRedisTemplate.opsForStream().pending(
                StreamNames.REVIEW,
                Consumer.from(ReviewStreamInitializer.GROUP_NAME, CONSUMER_NAME),
                Range.unbounded(),
                10
        );
        if (pending.isEmpty()) {
            log.debug("PEL 확인: 대기중인 메세지 없음");
            return;
        }

        log.info("PEL 확인: 총 {}건 대기중", pending.size());

        for (PendingMessage pendingMessage : pending) {
            RecordId id = pendingMessage.getId();
            long deliveryCount = pendingMessage.getTotalDeliveryCount();
            // 예: deliveryCount=1(최초만 전달됨, 재시도 0번) → retryCount=0
            long retryCount = deliveryCount - 1;
            Duration elapsed = pendingMessage.getElapsedTimeSinceLastDelivery();

            if (elapsed.compareTo(RECLAIM_IDLE_TIME) < 0) {
                log.info("재시도 대기중: {} ({}번째 재시도 예정, 대기 {}초 경과, {}초 후 재시도 가능)",
                        id, retryCount + 1, elapsed.getSeconds(), RECLAIM_IDLE_TIME.getSeconds() - elapsed.getSeconds());
                continue;
            } // idle 30초 안 지났으면 아직 처리 중일 수 있으니 스킵

            if (retryCount >= MAX_RETRY) {
                log.warn("최대 재시도 초과, DLQ로 이동: {} (재시도 {}회)", id, retryCount);
                dlqService.moveToDlq(id, retryCount);
                continue;
            }

            List<MapRecord<String, Object, Object>> claimed = stringRedisTemplate.opsForStream().claim(
                    StreamNames.REVIEW,
                    ReviewStreamInitializer.GROUP_NAME,
                    CONSUMER_NAME,
                    org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions
                            .minIdle(RECLAIM_IDLE_TIME)
                            .ids(id)
            );

            long attemptNumber = retryCount + 1;

            for (MapRecord<String, Object, Object> record : claimed) {
                log.info("재시도 메세지 회수: {} ({}번째 시도)", record.getId(), attemptNumber);
                handleRecord(record, attemptNumber);
            }
        }
    }



    private void handleRecord(MapRecord<String, Object, Object> record, long retryCount) {

        try {

            String payload = (String) record.getValue().get("payload");

            ReviewRequest reviewRequest = objectMapper.readValue(payload, ReviewRequest.class);

            ReviewResult result = codeReviewService.review(reviewRequest);
            slackService.sendMessage(result.contents());

            if(result.hasUsage()) {
                codeReviewService.saveSuccessReview(reviewRequest, result);
            }

            ack(record.getId());
            log.info("메세지 처리 완료 및 ACK: {}", record.getId());
        } catch (JacksonException e){
            log.error("메세지 처리 실패,Jackson 역직렬화 실패: {}", record.getId(), e);
            // 파싱 자체가 안 되는 메시지는 재시도해도 의미 없으니 바로 DLQ
            dlqService.moveToDlq(record.getId(), retryCount);
        } catch (Exception e){
            // 네트워크/일시적 오류 -> ACK 안 함, PEL에 남겨서 reclaimAndRetry 대상으로
            log.error("메세지 처리 실패: {}", record.getId(), e);
        }
    }

    private void ack(RecordId id) {
        stringRedisTemplate.opsForStream()
                .acknowledge(StreamNames.REVIEW, ReviewStreamInitializer.GROUP_NAME, id);
    }

}

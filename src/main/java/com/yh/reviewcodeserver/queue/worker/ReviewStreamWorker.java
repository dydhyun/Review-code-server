package com.yh.reviewcodeserver.queue.worker;

import com.yh.reviewcodeserver.queue.model.ReviewJob;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import com.yh.reviewcodeserver.service.review.CodeReviewServiceImpl;
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
    private final CodeReviewServiceImpl codeReviewServiceImpl;
    private final SlackService slackService;

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
            handleRecord(record, 1);
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
            Duration elapsed = pendingMessage.getElapsedTimeSinceLastDelivery();

            if (elapsed.compareTo(RECLAIM_IDLE_TIME) < 0) {
                log.info("재시도 대기중: {} ({}번째 시도 예정, 대기 {}초 경과, {}초 후 재시도 가능)",
                        id, deliveryCount + 1, elapsed.getSeconds(), RECLAIM_IDLE_TIME.getSeconds() - elapsed.getSeconds());
                continue;
            } // idle 30초 안 지났으면 아직 처리 중일 수 있으니 스킵

            if (deliveryCount > MAX_RETRY) {
                log.warn("최대 재시도 초과, DLQ로 이동: {} (시도 {}회)", id, deliveryCount);
                moveToDlq(id, deliveryCount);
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

            long attemptNumber = deliveryCount + 1;

            for (MapRecord<String, Object, Object> record : claimed) {
                log.info("재시도 메세지 회수: {} ({}번째 시도)", record.getId(), attemptNumber);
                handleRecord(record, attemptNumber);
            }
        }
    }



    private void handleRecord(MapRecord<String, Object, Object> record, long attemptNumber) {

        try {

            String payload = (String) record.getValue().get("payload");

            ReviewJob job = objectMapper.readValue(payload, ReviewJob.class);

            codeReviewServiceImpl.review(job.request());

            ack(record.getId());
            log.info("메세지 처리 완료 및 ACK: {}", record.getId());
        } catch (JacksonException e){
            log.error("메세지 처리 실패,Jackson 역직렬화 실패: {}", record.getId(), e);
            // 파싱 자체가 안 되는 메시지는 재시도해도 의미 없으니 바로 DLQ
            moveToDlq(record.getId(), attemptNumber);
        } catch (Exception e){
            // 네트워크/일시적 오류 -> ACK 안 함, PEL에 남겨서 reclaimAndRetry 대상으로
            log.error("메세지 처리 실패: {}", record.getId(), e);
        }
    }

    private void ack(RecordId id) {
        stringRedisTemplate.opsForStream()
                .acknowledge(StreamNames.REVIEW, ReviewStreamInitializer.GROUP_NAME, id);
    }

    // reclaimAndRetry에서 호출: ID만 있으니 내용을 조회해서 옮김
    private void moveToDlq(RecordId id, long attemptNumber) {
        List<MapRecord<String, Object, Object>> found = stringRedisTemplate.opsForStream().range(
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

        stringRedisTemplate.opsForStream().add(
                StreamRecords.newRecord()
                        .in(StreamNames.DLQ)
                        .ofMap(dlqBody)
        );

        ack(record.getId()); // 원본 스트림에서는 제거
        log.info("DLQ로 이동 완료: {} → {}", record.getId(), StreamNames.DLQ);

        notifyDlqFailure(payload);
    }

    private void notifyDlqFailure(String payload) {
        try {
            ReviewJob job = objectMapper.readValue(payload, ReviewJob.class);
            String repository = job.request().repository();
            slackService.sendMessage("⚠ [ " + repository + " ]" + " 리뷰 생성에 실패했습니다. 재시도 " + MAX_RETRY + " 회 초과.");
        }catch (Exception e){
            log.error("DLQ 실패 알림 전송 중 오류", e);
            slackService.sendMessage("⚠ 리뷰 생성에 실패했습니다.");
        }
    }


}

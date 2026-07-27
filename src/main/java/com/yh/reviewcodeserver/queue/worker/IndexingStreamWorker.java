package com.yh.reviewcodeserver.queue.worker;

import com.yh.reviewcodeserver.dto.IndexingRequest;
import com.yh.reviewcodeserver.entity.pgvector.CodeEmbeddingEntity;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import com.yh.reviewcodeserver.service.rag.CodeIndexingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class IndexingStreamWorker {

    private static final String CONSUMER_NAME = "indexing-worker-1";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CodeIndexingService codeIndexingService;

    @Scheduled(fixedRate = 1000)
    public void poll() {

        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream().read(
                Consumer.from(IndexingStreamInitializer.GROUP_NAME, CONSUMER_NAME),
                StreamReadOptions.empty().count(10),
                StreamOffset.create(StreamNames.INDEXING, ReadOffset.lastConsumed())
        );
        if (records.isEmpty()) {
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            handleRecord(record);
        }
    }

    // 재시도/DLQ 없음: 실패해도 로그만 남기고 ACK 처리
    // - 최초 인덱싱: 실패 시 사람이 workflow 재실행하면 됨 (멱등)
    // - 변경 파일 재인덱싱: 다음 Push 때 자연 복구됨
    private void handleRecord(MapRecord<String, Object, Object> record) {
        try {
            String payload = (String) record.getValue().get("payload");
            IndexingRequest request = objectMapper.readValue(payload, IndexingRequest.class);

            CodeEmbeddingEntity codeEmbeddingEntity = codeIndexingService.reindexFile(request.repoName(), request.filePath(), request.content());

            log.info("인덱싱 완료: {} / {}", codeEmbeddingEntity.getRepoName(), codeEmbeddingEntity.getFilePath());
        } catch (Exception e) {
            log.error("인덱싱 실패, 재시도 없이 스킵: {}", record.getId(), e);
        } finally {
            ack(record.getId());
        }
    }

    private void ack(RecordId id) {
        stringRedisTemplate.opsForStream()
                .acknowledge(StreamNames.INDEXING, IndexingStreamInitializer.GROUP_NAME, id);
    }
}
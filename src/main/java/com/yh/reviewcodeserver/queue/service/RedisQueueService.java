package com.yh.reviewcodeserver.queue.service;

import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class RedisQueueService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void enqueue(ReviewRequest request) {

        try {
            String payload = objectMapper.writeValueAsString(request);

            Map<String, String> body = Map.of(
                    "payload", payload
            );

            redisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(StreamNames.REVIEW)
                            .ofMap(body)
            );

        } catch (JacksonException e) {
            throw new IllegalStateException("ReviewJob 직렬화 실패", e);
        }

    }

}
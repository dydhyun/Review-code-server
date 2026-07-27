package com.yh.reviewcodeserver.queue.service;

import com.yh.reviewcodeserver.dto.IndexingRequest;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingQueueService {


    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void enqueue(IndexingRequest indexingRequest){
        try {
            log.info("request = {}", indexingRequest);
            String payload = objectMapper.writeValueAsString(indexingRequest);
            Map<String, String> fields = Map.of("payload", payload);
            redisTemplate.opsForStream().add(StreamNames.INDEXING, fields);
        } catch (JacksonException e){
            throw new IllegalStateException("인덱싱 요청 직렬화 실패", e);
        }
    }

}

package com.yh.reviewcodeserver.queue.worker;

import com.yh.reviewcodeserver.queue.model.StreamNames;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewStreamInitializer {

    private final StringRedisTemplate stringRedisTemplate;
    public static final String GROUP_NAME = "review-worker-group";

    @PostConstruct
    public void init() {
        try {
            stringRedisTemplate.opsForStream()
                    .createGroup(StreamNames.REVIEW, ReadOffset.from("0"), GROUP_NAME);
            log.info("Consumer Group [{}] 생성 완료", GROUP_NAME);
        } catch (Exception e) {
            log.info("Consumer Group [{}] 이미 존재함", GROUP_NAME);
        }
    }

}

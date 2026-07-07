package com.yh.reviewcodeserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest(properties = {"REVIEW_API_KEY=test-key"})
public class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void redisConnectionTest(){
        String pong = redisTemplate.getConnectionFactory().getConnection().ping();

        assertThat(pong).isEqualTo("PONG");
    }
}

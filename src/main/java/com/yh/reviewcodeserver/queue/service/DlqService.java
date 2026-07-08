package com.yh.reviewcodeserver.queue.service;

import com.yh.reviewcodeserver.queue.model.DlqItem;
import com.yh.reviewcodeserver.queue.model.StreamNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DlqService {

    private final StringRedisTemplate redisTemplate;

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

    public void retryReview(int recordId) {

    }
    
}

package com.yh.reviewcodeserver.client.openrouter;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;


@Component
@RequiredArgsConstructor
public class OpenRouterClient {

    private final RestTemplate restTemplate;
    private final OpenRouterProperties openRouterProperties;

    public String review(String diff){

        String prompt = """
                시니어 백엔드 개발자 관점으로 다음 코드 변경사항을 리뷰해줘.
                
                고려할 항목
                - 버그
                - 성능
                - 트랜잭션
                - 동시성
                - 클린코드
                
                변경사항:
                
                %s
                """.formatted(diff);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openRouterProperties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "gpt-5-mini",
                "input", prompt
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body,headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            "https://openrouter.ai/api/v1/chat/completions",
                            request,
                            String.class
                    );
            return response.getBody();

        } catch (HttpClientErrorException e) {
            return e.getResponseBodyAsString();
        }
    }

}

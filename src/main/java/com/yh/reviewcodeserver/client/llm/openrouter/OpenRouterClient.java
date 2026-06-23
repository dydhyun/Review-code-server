package com.yh.reviewcodeserver.client.llm.openrouter;

import com.yh.reviewcodeserver.Dto.openrouter.OpenRouterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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
                
                규칙
                - 최대 5개 항목
                - 문제의 중요도 포함
                - 문제가 없다면 "특별한 문제없음." 으로 반환
                - 300자 이내
                변경사항:
                
                %s
                """.formatted(diff);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openRouterProperties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", "openai/gpt-oss-120b:free",
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(body,headers);

        try {
            ResponseEntity<OpenRouterResponse> response =
                    restTemplate.postForEntity(
                            "https://openrouter.ai/api/v1/chat/completions",
                            request,
                            OpenRouterResponse.class
                    );

            List<OpenRouterResponse.Choice> openRouterChoices = response.getBody().getChoices();
            List<Integer> usages = List.of(
                    response.getBody().getUsage().getPrompt_tokens(),
                    response.getBody().getUsage().getCompletion_tokens(),
                    response.getBody().getUsage().getTotal_tokens());
            System.out.println(usages);

            if (openRouterChoices == null || openRouterChoices.isEmpty()) {
                throw new IllegalStateException("OpenRouter AI 응답이 비어있습니다.");
            }

            return "\uD83E\uDD16 AI Review \n"+openRouterChoices.get(0).getMessage().getContent();

        } catch (HttpClientErrorException e) {
            return e.getResponseBodyAsString();
        }
    }

}

package com.yh.reviewcodeserver.client.llm.openrouter;

import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.dto.ReviewResult;
import com.yh.reviewcodeserver.dto.openrouter.OpenRouterResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);
    private final RestTemplate restTemplate;
    private final OpenRouterProperties openRouterProperties;

    public ReviewResult review(ReviewRequest actionsRequest, List<String> ragContext){
        String commitInfo = actionsRequest.getCommitInfo();
        log.info(commitInfo);

        if (actionsRequest.getDiffSize() > 1000){
            return ReviewResult.withoutUsage(commitInfo + "변경사항이 많아 리뷰를 생략합니다.");
        }

        String diff = actionsRequest.diff();

        String prompt = getPrompt(diff, ragContext);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(openRouterProperties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
//                "model", "openai/gpt-oss-120b:free",
                "model", "poolside/laguna-m.1:free",
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        HttpEntity<Map<String, Object>> springToOpenRouterRequest =
                new HttpEntity<>(body,headers);


        try {
            long startTime = System.currentTimeMillis();

            log.info("OpenRouter 응답 요청");
            ResponseEntity<OpenRouterResponse> response =
                    restTemplate.postForEntity(
                            "https://openrouter.ai/api/v1/chat/completions",
                            springToOpenRouterRequest,
                            OpenRouterResponse.class
                    );

            OpenRouterResponse responseBody = response.getBody();
            log.info("OpenRouter 응답 수신 | 응답시간 : {}ms", System.currentTimeMillis() - startTime);

            if (responseBody == null) {
                // Optional 쓸 필요 없음. null 이면 코드 흐름을 여기서 끊어야 함
                throw new IllegalStateException("OpenRouter 응답 역직렬화 실패");
            }

            List<OpenRouterResponse.Choice> openRouterChoices = responseBody.getChoices();

            if (openRouterChoices == null || openRouterChoices.isEmpty()) {
                throw new IllegalStateException("OpenRouter AI 응답이 비어있습니다.");
            }

            int promptTokens = responseBody.getUsage().getPrompt_tokens();
            int completionTokens = responseBody.getUsage().getCompletion_tokens();

            log.info("토큰사용량 : {}, {}", promptTokens, completionTokens);

            String content = commitInfo +
                    "\uD83E\uDD16 AI Review \n" +
                    openRouterChoices.get(0).getMessage().getContent();

            return new ReviewResult(content,promptTokens, completionTokens);


        } catch (HttpClientErrorException.TooManyRequests e) {
            // 429에러 전용 처리. 발생하는 상황:
            // - 분당 요청 횟수 초과 (여러 레포에서 동시에 push)
            // - 일일 토큰 한도 초과 (무료 모델 사용 중)
            // - OpenRouter 자체 부하
            log.error("OpenRouter TooManyRequests error 발생 {}", e.getResponseBodyAsString());
            throw new IllegalStateException("OpenRouter Rate Limit 초과 재시도해주세요.", e);
        } catch (HttpClientErrorException e) {

            log.error("error 발생 {}", e.getResponseBodyAsString());
            return ReviewResult
                    .withoutUsage(commitInfo + "⚠ 리뷰 생성 실패: " + e.getStatusCode());
        }
    }


    private String getPrompt(String diff, List<String> ragContext){

        String joinedContext = String.join("\n", ragContext);

        return """
                시니어 백엔드 개발자 관점으로 다음 코드 변경사항(Change Request)을 리뷰해줘.
                
                고려할 항목
                - 제공된 Context(클래스/필드/메서드 시그니처)와 변경된 코드 간의 타입, 호출 방식, 도메인 규칙 일치 여부 및 사이드 이펙트
                - 잘못 작성된 코드 및 잠재적 버그 확인
                - 코드 개선 제안 및 클린코드 규칙 준수 여부
                - 중복 코드 및 기존 프로젝트와의 일관성 유지
                
                규칙
                - 지적 사항은 최대 5개 항목까지만 작성
                - 문제가 전혀 없다면 구조를 무시하고 "특이사항 없음." 만 반환
                - 문제점은 연관 키워드를 중심으로 항목당 1~2줄 내외로 간결하게 작성 (전체 분량 요약 지향)
                
                리뷰 포맷은 다음 예시 형식으로 고정해서 출력해줘.
                
                [U+1F7E5 HIGH]
                고려된 항목 | 문제점 + 코멘트
                고려된 항목 | 문제점 + 코멘트
                [U+1F7E8 MEDIUM]
                고려된 항목 | 문제점 + 코멘트
                [U+1F7E9 LOW]
                고려된 항목 | 문제점 + 코멘트
                
                ※ 각 중요도에 해당하는 리뷰 항목이 없다면 아래와 같이 하위에 대시(-)만 남겨줘.
                [🟨 MEDIUM]
                -
                
                Change Request (변경 사항)
                %s
                
                Context (프로젝트 구조 및 관련 시그니처)
                %s
                """.formatted(diff, joinedContext);
    }

}

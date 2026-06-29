package com.yh.reviewcodeserver.client.llm.openrouter;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.Dto.openrouter.OpenRouterResponse;
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

    public String review(ReviewRequest actionsRequest){
        String commitInfo = getCommitInfo(actionsRequest);
        log.info(commitInfo);

        if (actionsRequest.getDiffSize() > 1000){
            return commitInfo + "변경사항이 많아 리뷰를 생략합니다.";
        }

        String diff = actionsRequest.diff();

        String prompt = getPrompt(diff);

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

            List<Integer> usages = List.of(
                    responseBody.getUsage().getPrompt_tokens(),
                    responseBody.getUsage().getCompletion_tokens(),
                    responseBody.getUsage().getTotal_tokens());

            if (openRouterChoices == null || openRouterChoices.isEmpty()) {
                throw new IllegalStateException("OpenRouter AI 응답이 비어있습니다.");
            }
            log.info("토큰사용량 : {}", usages);
            return commitInfo +
                    "\uD83E\uDD16 AI Review \n" +
                    openRouterChoices.get(0).getMessage().getContent();


        } catch (HttpClientErrorException.TooManyRequests e) {
            // 429에러 전용 처리. 발생하는 상황:
            // - 분당 요청 횟수 초과 (여러 레포에서 동시에 push)
            // - 일일 토큰 한도 초과 (무료 모델 사용 중)
            // - OpenRouter 자체 부하
            log.error("OpenRouter TooManyRequests error 발생 {}", e.getResponseBodyAsString());
            return commitInfo +
                    "⚠ OpenRouter Rate Limit 발생. 잠시 후 재시도해주세요.";
        } catch (HttpClientErrorException e) {

            log.error("error 발생 {}", e.getResponseBodyAsString());
            return commitInfo +
                    "⚠ 리뷰 생성 실패: " + e.getStatusCode();
        }
    }



    private String getCommitInfo(ReviewRequest actionsRequest) {

        String repository = actionsRequest.repository();
        String authorName = actionsRequest.author();
        String commitMessage = actionsRequest.commitMessage();

        return """
                Repository : %s
                Author : %s
                Commit : %s
                """.formatted(repository,authorName,commitMessage);
    }


    private String getPrompt(String diff){

        return """
                시니어 백엔드 개발자 관점으로 다음 코드 변경사항을 리뷰해줘.
                
                고려할 항목
                - 잘못 작성된 코드, 잠재적 버그 확인
                - 코드 개선 제안
                - 클린코드
                - 중복코드
                - 코드 일관성 유지
                규칙
                - 최대 5개 항목
                - 문제의 중요도 산정
                - 문제가 없다면 "특이사항 없음." 으로 반환
                - 문제에 대해 간단하게 언급하고 연관 키워드 중심으로 리뷰
                - 300자 이내
                리뷰 포맷은 다음 예시와 같이 고정해서 리뷰해줘.
                [U+1F7E5 HIGH]
                고려된 항목 | 문제점 + 코멘트
                고려된 항목 | 문제점 + 코멘트
                [U+1F7E8 MEDIUM]
                고려된 항목 | 문제점 + 코멘트
                [U+1F7E9 LOW]
                고려된 항목 | 문제점 + 코멘트
                
                각 중요도에 해당하는 리뷰할 문제점/항목이 없다면 - 로 대체해줘.
                예를 들어 MEDIUM에 대한 리뷰가 없다면,
                [U+1F7E5 HIGH]
                고려된 항목 | 문제점 + 코멘트
                고려된 항목 | 문제점 + 코멘트
                [U+1F7E8 MEDIUM]
                -
                [U+1F7E9 LOW]
                고려된 항목 | 문제점 + 코멘트
                
                변경사항은 다음과 같아
                %s
                """.formatted(diff);
    }
}

package com.yh.reviewcodeserver.client.llm.openrouter;

import com.yh.reviewcodeserver.dto.ReviewRequest;
import com.yh.reviewcodeserver.dto.openrouter.OpenRouterResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OpenRouterClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OpenRouterProperties openRouterProperties;

    @InjectMocks
    private OpenRouterClient openRouterClient;


    private void setUpUsage(OpenRouterResponse.Usage usage, int promptTokens, int completionToken, int totalToken){
        usage.setPrompt_tokens(promptTokens);
        usage.setCompletion_tokens(completionToken);
        usage.setTotal_tokens(totalToken);
    }

    private ReviewRequest request;

    // given
    @BeforeEach
    void setUp() {
        request = new ReviewRequest(
                "test diff",
                "dydhyun",
                "dydhyun/Review-code-server",
                "feat: 테스트 코드 작성",
                "commitId",
                "1"
        );
    }

    @Test
    void 정상응답_시_리뷰텍스트_반환() {


        OpenRouterResponse.OpenRouterMessage message = new OpenRouterResponse.OpenRouterMessage();
        message.setContent("review: HIGH\n 클린코드 개선 필요");

        OpenRouterResponse.Choice choice = new OpenRouterResponse.Choice();
        choice.setMessage(message);

        OpenRouterResponse.Usage usage = new OpenRouterResponse.Usage();

        setUpUsage(usage,100,200,300);

        OpenRouterResponse mockResponse = new OpenRouterResponse();
        mockResponse.setChoices(List.of(choice));
        mockResponse.setUsage(usage);

        when(openRouterProperties.getApiKey()).thenReturn("test-api-key");
        // url , HttpEntity, OpenRouterResponse
        when(restTemplate.postForEntity(any(String.class), any(), eq(OpenRouterResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when
        String result = openRouterClient.review(request);
//        System.out.println(result);

        // then
        assertThat(result).contains("🤖 AI Review");
        assertThat(result).contains("dydhyun/Review-code-server");

    }


    @Test
    void 응답바디_null_시_IllegalStateException_발생() {


        when(openRouterProperties.getApiKey()).thenReturn("test-api-key");
        when(restTemplate.postForEntity(any(String.class), any(), eq(OpenRouterResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        // when then
        assertThatThrownBy(() -> openRouterClient.review(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenRouter 응답 역직렬화 실패");
    }


    @Test
    void choices_비어있을_시_IllegalStateException_발생() {

        OpenRouterResponse.Usage usage = new OpenRouterResponse.Usage();

        OpenRouterResponse mockResponse = new OpenRouterResponse();
        mockResponse.setChoices(List.of());
        mockResponse.setUsage(usage);

        when(openRouterProperties.getApiKey()).thenReturn("test-api-key");
        when(restTemplate.postForEntity(any(String.class), any(), eq(OpenRouterResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when then
        assertThatThrownBy(() -> openRouterClient.review(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OpenRouter AI 응답이 비어있습니다.");
    }


    @Test
    void 요청횟수_초과_시_RateLimit_메시지_반환(){


        when(openRouterProperties.getApiKey()).thenReturn("test-api-key");
        when(restTemplate.postForEntity(any(String.class), any(), eq(OpenRouterResponse.class)))
                .thenThrow(HttpClientErrorException.TooManyRequests.create(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too Many Requests",
                        null,
                        null,
                        null
                ));

        //when
        String result = openRouterClient.review(request);

        //then
        assertThat(result).contains("⚠ OpenRouter Rate Limit 발생. 잠시 후 재시도해주세요.");
    }


    @Test
    void HTTP_에러_발생_시_실패_메시지_반환(){


        when(openRouterProperties.getApiKey()).thenReturn("test-api-key");
        when(restTemplate.postForEntity(any(String.class), any(), eq(OpenRouterResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        // when
        String result = openRouterClient.review(request);
        // then
        assertThat(result).contains("리뷰 생성 실패");
    }


    @Test
    void Diff_1000줄_초과_시_리뷰생략_메시지_반환(){

        String largeDiff = "line\n".repeat(1001);

        ReviewRequest largeDiffRequest = new ReviewRequest(
                largeDiff,
                "dydhyun",
                "dydhyun/Review-code-server",
                "feat: 테스트 코드 작성",
                "commitId",
                "1"
        );

        String result = openRouterClient.review(largeDiffRequest);

        assertThat(result).contains("변경사항이 많아 리뷰를 생략합니다.");
    }


}

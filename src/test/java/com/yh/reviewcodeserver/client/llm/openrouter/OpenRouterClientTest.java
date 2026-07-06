package com.yh.reviewcodeserver.client.llm.openrouter;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.Dto.openrouter.OpenRouterResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatException;
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

    @Test
    void 정상응답_시_리뷰텍스트_반환(){
        //given
        ReviewRequest request = new ReviewRequest(
                "test diff",
                "dydhyun",
                "dydhyun/Review-code-server",
                "feat: 테스트 코드 작성"
        );

        OpenRouterResponse.OpenRouterMessage message = new OpenRouterResponse.OpenRouterMessage();
        message.setContent("review: HIGH\n 클린코드 개선 필요");

        OpenRouterResponse.Choice choice = new OpenRouterResponse.Choice();
        choice.setMessage(message);

        OpenRouterResponse.Usage usage = new OpenRouterResponse.Usage();
        usage.setPrompt_tokens(100);
        usage.setCompletion_tokens(200);
        usage.setTotal_tokens(300);

        OpenRouterResponse mockResponse = new OpenRouterResponse();
        mockResponse.setChoices(List.of(choice));
        mockResponse.setUsage(usage);

        when(openRouterProperties.getApiKey()).thenReturn("test-api-key");
        // url , HttpEntity, OpenRouterResponse
        when(restTemplate.postForEntity(any(String.class), any(), eq(OpenRouterResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // when
        String result = openRouterClient.review(request);
        System.out.println(result);

        // then
        assertThat(result).contains("🤖 AI Review");
        assertThat(result).contains("dydhyun/Review-code-server");

    }


}

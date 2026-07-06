package com.yh.reviewcodeserver.service;

import com.yh.reviewcodeserver.Dto.ReviewRequest;
import com.yh.reviewcodeserver.client.llm.openrouter.OpenRouterClient;
import com.yh.reviewcodeserver.service.review.CodeReviewServiceImpl;
import com.yh.reviewcodeserver.service.slack.SlackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CodeReviewServiceTest {

    @Mock
    private OpenRouterClient openRouterClient;

    @Mock
    private SlackService slackService;

    @InjectMocks
    private CodeReviewServiceImpl codeReviewService;

    private ReviewRequest request;

    @BeforeEach
    void setUp() {
        request = new ReviewRequest(
                "test diff",
                "dydhyun",
                "dydhyun/Review-code-server",
                "feat: 테스트 코드 작성"
        );
    }

    // 1. 정상 흐름 - OpenRouterClient → SlackService 순서로 호출되는지
    @Test
    void 리뷰_요청_시_OpenRouter_호출_후_Slack_전송(){
        //given
        when(openRouterClient.review(request)).thenReturn("AI Review\n리뷰결과");

        //when
        codeReviewService.review(request);

        //then
        verify(openRouterClient).review(request);
        verify(slackService).sendMessage("AI Review\n리뷰결과");

    }


    // 2. diff가 null 또는 빈값이면 OpenRouter 호출 안 하는지
    @Test
    void diff_없을_시_리뷰_생략(){
        //given
        ReviewRequest noDiffRequest = new ReviewRequest(
                "",
                "dydhyun",
                "dydhyun/Review-code-server",
                "feat: 테스트 코드 작성"
        );

        //when
        codeReviewService.review(noDiffRequest);

        //then
        verify(openRouterClient, never()).review(any());
        verify(slackService, never()).sendMessage(any());

    }

}

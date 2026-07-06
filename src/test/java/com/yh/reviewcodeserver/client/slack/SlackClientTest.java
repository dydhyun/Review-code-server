package com.yh.reviewcodeserver.client.slack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SlackClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private SlackProperties slackProperties;

    @InjectMocks
    private SlackClient slackClient;

    @Test
    void 슬랙_메시지_정상_전송(){
        //given
        when(slackProperties.getWebhookUrl()).thenReturn("https://슬랙_url_test");
        //when
        slackClient.sendMessage("테스트 메세지");
        //then
        verify(restTemplate).postForObject(
                eq("https://슬랙_url_test"),
                any()
                ,eq(String.class)
        );
    }


    @Test
    void 슬랙_전송_실패_시_예외_전파(){
        // given
        when(slackProperties.getWebhookUrl()).thenReturn("슬랙_url");
        when(restTemplate.postForObject(any(String.class), any(), eq(String.class)))
                .thenThrow(new RestClientException("Slack 연결 실패"));

        // when & then
        assertThatThrownBy(() -> slackClient.sendMessage("테스트 메시지"))
                .isInstanceOf(RestClientException.class)
                .hasMessage("Slack 연결 실패");
    }

}

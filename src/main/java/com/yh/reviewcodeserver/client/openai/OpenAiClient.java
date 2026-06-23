package com.yh.reviewcodeserver.client.openai;

import org.springframework.stereotype.Component;

@Component
public class OpenAiClient {

    public String review(String diff){

        return """
                리뷰 결과

                - 변경사항 확인
                - 추가 검토 필요
                """;
    }
}

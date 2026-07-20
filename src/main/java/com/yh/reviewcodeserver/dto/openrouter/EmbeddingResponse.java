package com.yh.reviewcodeserver.dto.openrouter;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class EmbeddingResponse {
    private List<Data> data;

    @Getter
    @Setter
    public static class Data{
        private List<Double> embedding;
    }
}

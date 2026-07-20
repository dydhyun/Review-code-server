package com.yh.reviewcodeserver.service.rag;

import com.yh.reviewcodeserver.dto.openrouter.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmbeddingClient {

    private final RestTemplate restTemplate;

    @Value("${openrouter.api-key}")
    private String apiKey;

    @Value("${nvidia/nemotron-3-embed-1b:free}")
    private String embeddingModel;

    private static final String EMBEDDING_URL = "https://openrouter.ai/api/v1/embeddings";


    public float[] embed(String signatureText){

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "model", embeddingModel,
                "input", signatureText
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<EmbeddingResponse> response =
                restTemplate.postForEntity(EMBEDDING_URL, request, EmbeddingResponse.class);

        return parseEmbedding(response);
    }


    private float[] parseEmbedding(ResponseEntity<EmbeddingResponse> response) {

        EmbeddingResponse responseBody = response.getBody();
        if (responseBody == null || responseBody.getData() == null || responseBody.getData().isEmpty()) {
            throw new IllegalStateException("임베딩 응답이 비어있습니다.");
        }

        List<Double> embedding = responseBody.getData().get(0).getEmbedding();
        float[] result = new float[embedding.size()];
        for (int i = 0; i <embedding.size(); i++){
            result[i] = embedding.get(i).floatValue();
        }

        return result;
    }


}

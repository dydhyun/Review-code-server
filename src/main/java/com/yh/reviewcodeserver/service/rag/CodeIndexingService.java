package com.yh.reviewcodeserver.service.rag;

import com.yh.reviewcodeserver.client.llm.openrouter.EmbeddingClient;
import com.yh.reviewcodeserver.entity.pgvector.CodeEmbeddingEntity;
import com.yh.reviewcodeserver.repository.pgvector.CodeEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeIndexingService {

    private final SignatureExtractor signatureExtractor;
    private final EmbeddingClient embeddingClient;
    private final CodeEmbeddingRepository codeEmbeddingRepository;

    private CodeEmbeddingEntity upsert(String repoName, String filePath, SourceFile sourceFile) {
        Optional<CodeEmbeddingEntity> existing =
                codeEmbeddingRepository.findByRepoNameAndFilePath(repoName, filePath);

        String signature = signatureExtractor.extract(sourceFile);
        float[] vector = embeddingClient.embed(signature);

        if (existing.isPresent()) {
            CodeEmbeddingEntity entity = existing.get();
            entity.updateEmbedding(signature, vector, LocalDateTime.now());
            return entity;
        }
        return codeEmbeddingRepository.save(buildEntity(repoName, filePath, signature, vector));
    }


    @Transactional
    public CodeEmbeddingEntity reindexFile(String repoName, String filePath, String content) {
        return upsert(repoName, filePath, new SourceFile(filePath, content));
    }

    // 이미 계산된 signature/vector로 순수 신규 엔티티만 조립
    private CodeEmbeddingEntity buildEntity(String repoName, String filePath, String signature, float[] vector) {
        return CodeEmbeddingEntity.builder()
                .repoName(repoName)
                .filePath(filePath)
                .content(signature)
                .embedding(vector)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}

package com.yh.reviewcodeserver.service.rag;

import com.yh.reviewcodeserver.entity.pgvector.CodeEmbeddingEntity;
import com.yh.reviewcodeserver.repository.pgvector.CodeEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeIndexingService {

    private final CodeSourceProvider codeSourceProvider;
    private final SignatureExtractor signatureExtractor;
    private final EmbeddingClient embeddingClient;
    private final CodeEmbeddingRepository codeEmbeddingRepository;

    public void indexRepoIfNeeded(String repoName){

        if (codeEmbeddingRepository.existsByRepoName(repoName)){
            log.info("이미 인덱싱 된 레포입니다. repoName = {}", repoName);
            return;
        }
        log.info("최초 인덱싱을 시작합니다. repoName = {}",repoName);

        List<SourceFile> sourceFiles = codeSourceProvider.fetchAllFiles(repoName);
        log.info("codeSourceProvider로 반환된 List sourceFiles = {}", sourceFiles);

        List<CodeEmbeddingEntity> entities = sourceFiles.stream()
                .filter(sourceFile -> !sourceFile.filePath().contains("src\\test\\"))
                .map(sourceFile -> toNewEntity(repoName, sourceFile))
                .filter(java.util.Objects::nonNull)
                .toList();

        codeEmbeddingRepository.saveAll(entities);
        log.info("전체 인덱싱 완료. repoName = {}", repoName);

    }

    @Transactional
    public CodeEmbeddingEntity reindexFile(String repoName, String filePath){

        Optional<CodeEmbeddingEntity> existing =
                codeEmbeddingRepository.findByRepoNameAndFilePath(repoName, filePath);

        SourceFile sourceFile = codeSourceProvider.fetchFile(repoName, filePath);
        String signature = signatureExtractor.extract(sourceFile);
        float[] vector = embeddingClient.embed(signature);

        if (existing.isPresent()) {
            CodeEmbeddingEntity entity = existing.get();
            entity.updateEmbedding(signature, vector, LocalDateTime.now());
            log.info("파일 재인덱싱 완료(update). repoName={}, filePath={}", repoName, filePath);
            return entity; // 트랜잭션 커밋 시 Dirty Checking
        }


        CodeEmbeddingEntity saved = codeEmbeddingRepository.save(
                buildEntity(repoName, filePath, signature, vector));
        log.info("파일 재인덱싱 완료. repoName = {}, filePath = {} isUpdate = {}",
                repoName, filePath, existing.isPresent());

        return saved;
    }

    // 최초 전체 인덱싱 전용 - 시그니처 추출부터 담당
    private CodeEmbeddingEntity toNewEntity(String repoName, SourceFile sourceFile) {
        String signature = signatureExtractor.extract(sourceFile);

        if (signature.isBlank()) {
            log.warn("시그니처를 추출할 수 없어 인덱싱을 건너뜁니다. filePath={}", sourceFile.filePath());
            return null;
        }

        float[] vector = embeddingClient.embed(signature);
        return buildEntity(repoName, sourceFile.filePath(), signature, vector);
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

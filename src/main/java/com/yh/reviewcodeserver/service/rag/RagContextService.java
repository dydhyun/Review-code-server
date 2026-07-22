package com.yh.reviewcodeserver.service.rag;

import com.yh.reviewcodeserver.entity.pgvector.CodeEmbeddingEntity;
import com.yh.reviewcodeserver.repository.pgvector.CodeEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagContextService {

    private static final int TOP_K = 3 + 1;

    private final CodeEmbeddingRepository codeEmbeddingRepository;

    // Worker가 reindexFile로 이미 계산해둔 벡터를 그대로 받아서 검색만 수행
    public List<String> retrieveContext(String repoName, String changedFilePath, float[] embedding){

//        String queryVector = toLiteral(embedding);

        List<CodeEmbeddingEntity> similar =
                codeEmbeddingRepository.findSimilar(repoName, embedding, TOP_K);

        return similar.stream()
                .filter(entity -> !entity.getFilePath().equals(changedFilePath))
                .limit(TOP_K)
                .map(CodeEmbeddingEntity::getContent)
                .toList();
    }

//    private static String toLiteral(float[] vector) {
//        StringBuilder sb = new StringBuilder("[");
//        for (int i = 0; i < vector.length; i++) {
//            if (i > 0) sb.append(",");
//            sb.append(vector[i]);
//        }
//        return sb.append("]").toString();
//    }

}

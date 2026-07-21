package com.yh.reviewcodeserver.repository.pgvector;

import com.yh.reviewcodeserver.entity.pgvector.CodeEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CodeEmbeddingRepository extends JpaRepository<CodeEmbeddingEntity, Long> {

    boolean existsByRepoName(String repoName);

    Optional<CodeEmbeddingEntity> findByRepoNameAndFilePath(String repoName, String filePath);


    @Query(value = """
        SELECT * FROM code_embeddings
        WHERE repo_name = :repoName
        ORDER BY embedding <=> CAST(:queryVector AS vector)
        LIMIT :topK
        """, nativeQuery = true)
    List<CodeEmbeddingEntity> findSimilar(
            @Param("repoName") String repoName,
            @Param("queryVector") String queryVector, // "[0.1,0.2,...]" 형태 문자열로 전달
            @Param("topK") int topK
    );
}

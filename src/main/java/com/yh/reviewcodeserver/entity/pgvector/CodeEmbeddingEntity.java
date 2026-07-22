package com.yh.reviewcodeserver.entity.pgvector;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "code_embeddings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"repo_name", "file_path"})
)@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeEmbeddingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_name", nullable = false)
    private String repoName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "embedding", columnDefinition = "vector(2048)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] embedding;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void updateEmbedding(String content, float[] embedding, LocalDateTime updatedAt) {
        this.content = content;
        this.embedding = embedding;
        this.updatedAt = updatedAt;
    }

}

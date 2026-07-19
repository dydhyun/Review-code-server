package com.yh.reviewcodeserver.entity.pgvector;

import com.pgvector.PGvector;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "code_embeddings")
@Getter
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

    @Column(name = "embedding", columnDefinition = "vector(1536)")
    @Convert(converter = VectorConverter.class)
    private float[] embedding;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;


    // float[] <-> PGvector 변환 담당
    public static class VectorConverter implements jakarta.persistence.AttributeConverter<float[], PGvector> {
        @Override
        public PGvector convertToDatabaseColumn(float[] attribute) {
            return attribute == null ? null : new PGvector(attribute);
        }

        @Override
        public float[] convertToEntityAttribute(PGvector dbData) {
            return dbData == null ? null : dbData.toArray();
        }
    }

}

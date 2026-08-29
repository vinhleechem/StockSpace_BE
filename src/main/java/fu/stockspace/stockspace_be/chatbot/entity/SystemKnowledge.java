package fu.stockspace.stockspace_be.chatbot.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Objects;
import java.util.UUID;





@Entity
@Table(name = "system_knowledge", indexes = {
        @Index(name = "idx_system_knowledge_category", columnList = "category"),
        @Index(name = "idx_system_knowledge_source_id", columnList = "source_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SystemKnowledge extends BaseEntity {

    public static final int EMBEDDING_DIMENSIONS = 1536;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private KnowledgeCategory category;





    @Column(name = "source_id", length = 100, unique = true)
    private String sourceId;


    @Column(name = "title", nullable = false, length = 255)
    private String title;


    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;


    @Column(name = "embedding_str", columnDefinition = "TEXT")
    private String embeddingStr;





    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSIONS)
    @Column(name = "embedding_vector", columnDefinition = "vector(1536)")
    private float[] embeddingVector;


    @Column(name = "embedding_model", length = 150)
    private String embeddingModel;


    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;


    @Column(name = "content_hash", length = 64)
    private String contentHash;






    public void setTitle(String title) {
        if (!Objects.equals(this.title, title)) {
            clearEmbedding();
        }
        this.title = title;
    }

    public void setContent(String content) {
        if (!Objects.equals(this.content, content)) {
            clearEmbedding();
        }
        this.content = content;
    }

    public void clearEmbedding() {
        this.embeddingVector = null;
        this.embeddingStr = null;
        this.embeddingModel = null;
        this.embeddingDimensions = null;
        this.contentHash = null;
    }
}

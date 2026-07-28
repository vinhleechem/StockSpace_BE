package fu.stockspace.stockspace_be.chatbot.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Entity lưu trữ dữ liệu tri thức hệ thống (Chính sách, Điều khoản, FAQ)
 * phục vụ cho RAG (Retrieval-Augmented Generation).
 */
@Entity
@Table(name = "system_knowledge", indexes = {
        @Index(name = "idx_system_knowledge_category", columnList = "category")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SystemKnowledge extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Phân loại: POLICY, FAQ, CANCELLATION, INSURANCE, RENTAL_PROCESS */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /** Tiêu đề chính sách / câu hỏi FAQ */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /** Nội dung chi tiết chính sách / câu trả lời FAQ */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Vector embedding (chuyển sang JSON/TEXT literal "[...]") */
    @Column(name = "embedding_str", columnDefinition = "TEXT")
    private String embeddingStr;
}

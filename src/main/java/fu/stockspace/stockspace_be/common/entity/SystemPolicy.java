package fu.stockspace.stockspace_be.common.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * Entity SystemPolicy — chính sách hệ thống chứa cam kết ràng buộc pháp lý.
 * Map với bảng: system_policies
 */
@Entity
@Table(name = "system_policies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SystemPolicy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "version", unique = true, nullable = false, length = 50)
    private String version;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
}

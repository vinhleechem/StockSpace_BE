package fu.stockspace.stockspace_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;

/**
 * Permission entity — lưu vào bảng `permissions`.
 *
 * Tên permission dùng theo convention: NOUN_ACTION
 * Ví dụ: WAREHOUSE_CREATE, INVENTORY_READ, STAFF_MANAGE
 *
 * Dùng trong @PreAuthorize:
 *   @PreAuthorize("hasAuthority('WAREHOUSE_CREATE')")
 */
@Entity
@org.hibernate.annotations.SQLDelete(sql = "UPDATE permissions SET is_deleted = true WHERE id = ?")
@org.hibernate.annotations.SQLRestriction("is_deleted = false")
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Permission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tên permission — unique, viết HOA, dùng _ phân cách.
     * Ví dụ: "WAREHOUSE_CREATE", "INVENTORY_READ"
     */
    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;
}

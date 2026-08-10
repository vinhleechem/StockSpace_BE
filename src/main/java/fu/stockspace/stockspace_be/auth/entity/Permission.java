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
 *   @PreAuthorize("@rbac.hasPermission('WAREHOUSE_CREATE')")
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Permission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;

    /**
     * Tên permission — unique, viết HOA, dùng _ phân cách.
     * Ví dụ: "WAREHOUSE_CREATE", "INVENTORY_READ"
     */
    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;
}

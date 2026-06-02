package fu.stockspace.stockspace_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

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
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tên permission — unique, viết HOA, dùng _ phân cách.
     * Ví dụ: "WAREHOUSE_CREATE", "INVENTORY_READ"
     */
    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;
}

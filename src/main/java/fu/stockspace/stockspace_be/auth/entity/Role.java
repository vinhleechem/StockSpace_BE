package fu.stockspace.stockspace_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Role entity — lưu vào bảng `roles`.
 *
 * Khác với enum RoleType (cố định), Role entity có thể được tạo thêm
 * bởi Admin qua API (/api/admin/roles).
 *
 * Ví dụ: Admin tạo role "ROLE_SUPERVISOR" mới mà không cần sửa code.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tên role — luôn có prefix ROLE_ để Spring Security nhận diện.
     * Ví dụ: "ROLE_ADMIN", "ROLE_OWNER", "ROLE_SUPERVISOR"
     */
    @Column(name = "name", unique = true, nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    /**
     * Danh sách permissions của role này.
     * Khi load User → load Role → load Permission (EAGER để tránh LazyInitializationException trong getAuthorities())
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}

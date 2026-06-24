package fu.stockspace.stockspace_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Entity User — đây là base account của toàn bộ hệ thống.
 *
 * Implement UserDetails để tích hợp thẳng với Spring Security,
 * tránh phải tạo wrapper class riêng.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends BaseEntity implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    /** Provider đăng nhập: LOCAL hoặc GOOGLE */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20, columnDefinition = "varchar(20) default 'LOCAL'")
    @Builder.Default
    private AuthProvider provider = AuthProvider.LOCAL;

    /** URL ảnh đại diện (lấy từ Google khi login bằng OAuth) */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private java.util.Set<Role> roles = new java.util.HashSet<>();


    // ==================== UserDetails impl ====================

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        java.util.Set<SimpleGrantedAuthority> authorities = new java.util.HashSet<>();
        for (Role role : roles) {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
            if (role.getPermissions() != null) {
                for (Permission permission : role.getPermissions()) {
                    authorities.add(new SimpleGrantedAuthority(permission.getName()));
                }
            }
        }
        return authorities;
    }

    @Override
    public String getUsername() {
        // Spring Security dùng email làm username
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive();
    }
}

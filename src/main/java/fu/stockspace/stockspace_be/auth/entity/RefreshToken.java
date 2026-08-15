package fu.stockspace.stockspace_be.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;

import java.time.LocalDateTime;








@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private java.util.UUID id;






    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;




    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;




    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;




    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiresAt);
    }
}

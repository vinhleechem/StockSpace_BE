package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.PasswordResetToken;
import fu.stockspace.stockspace_be.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByUserAndToken(User user, String token);

    /** Xóa tất cả token cũ của user trước khi tạo mới */
    @Modifying
    @Query("DELETE FROM PasswordResetToken p WHERE p.user = :user")
    void deleteAllByUser(@Param("user") User user);
}

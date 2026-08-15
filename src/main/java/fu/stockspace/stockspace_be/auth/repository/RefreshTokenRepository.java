package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.RefreshToken;
import fu.stockspace.stockspace_be.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, java.util.UUID> {




    @Query("SELECT rt FROM RefreshToken rt WHERE rt.token = :token AND rt.isDeleted = false")
    Optional<RefreshToken> findByToken(String token);




    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isDeleted = true WHERE rt.user = :user")
    void deleteAllByUser(User user);




    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isDeleted = true WHERE rt.token = :token")
    void deleteByToken(String token);




    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isDeleted = true WHERE rt.expiresAt < :now")
    void deleteAllExpiredBefore(LocalDateTime now);
}

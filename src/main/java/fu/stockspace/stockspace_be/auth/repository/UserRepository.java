package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho User entity.
 * Dev 2: Nếu cần thêm query, hãy extends repo này hoặc tạo custom query — đừng sửa file này.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Tìm user theo email — dùng cho login và load UserDetails.
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra email đã tồn tại chưa — dùng khi register.
     */
    boolean existsByEmail(String email);
}

package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tìm user theo email — dùng cho login và load UserDetails.
     */
    Optional<User> findByEmail(String email);

    /**
     * Kiểm tra email đã tồn tại chưa — dùng khi register.
     */
    boolean existsByEmail(String email);

    /**
     * Tìm danh sách người dùng được gán một role cụ thể.
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.id = :roleId")
    List<User> findUsersByRoleId(@Param("roleId") Long roleId);

    /**
     * Tìm kiếm user theo email / fullName / phone với phân trang.
     * Dùng cho trang quản lý người dùng của Admin.
     */
    @Query("SELECT u FROM User u WHERE " +
           "(:keyword IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Tìm kiếm user theo keyword + filter theo role name.
     */
    @Query("SELECT DISTINCT u FROM User u JOIN u.roles r WHERE " +
           "r.name = :roleName AND " +
           "(:keyword IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsersByRole(@Param("keyword") String keyword,
                                 @Param("roleName") String roleName,
                                 Pageable pageable);

    /**
     * Tìm kiếm user theo keyword + filter theo isActive.
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.isActive = :isActive AND " +
           "(:keyword IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR u.phone LIKE CONCAT('%', :keyword, '%'))")
    Page<User> searchUsersByStatus(@Param("keyword") String keyword,
                                   @Param("isActive") boolean isActive,
                                   Pageable pageable);
}


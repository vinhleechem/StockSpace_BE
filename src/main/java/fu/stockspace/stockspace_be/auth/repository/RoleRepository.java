package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository cho Role entity.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, java.util.UUID> {

    @Query("SELECT r FROM Role r WHERE r.id = ?1 AND r.isDeleted = false")
    Optional<Role> findById(java.util.UUID id);

    @Query("SELECT r FROM Role r WHERE r.isDeleted = false")
    java.util.List<Role> findAll();

    /**
     * Tìm Role theo tên.
     * Ví dụ: "ROLE_ADMIN", "ROLE_OWNER"
     */
    @Query("SELECT r FROM Role r WHERE r.name = ?1 AND r.isDeleted = false")
    Optional<Role> findByName(String name);
}

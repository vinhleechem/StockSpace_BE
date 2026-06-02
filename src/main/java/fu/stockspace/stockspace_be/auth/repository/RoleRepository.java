package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho Role entity.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    /**
     * Tìm Role theo tên.
     * Ví dụ: "ROLE_ADMIN", "ROLE_OWNER"
     */
    Optional<Role> findByName(String name);
}

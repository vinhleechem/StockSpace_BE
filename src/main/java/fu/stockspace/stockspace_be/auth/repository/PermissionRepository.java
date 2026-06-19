package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository cho Permission entity.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, java.util.UUID> {

    /**
     * Tìm Permission theo tên.
     * Ví dụ: "WAREHOUSE_READ", "INVENTORY_WRITE"
     */
    Optional<Permission> findByName(String name);
}

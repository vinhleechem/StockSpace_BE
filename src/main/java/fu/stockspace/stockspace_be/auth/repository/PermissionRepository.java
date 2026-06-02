package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository cho Permission entity.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    /**
     * Tìm Permission theo tên.
     * Ví dụ: "WAREHOUSE_READ", "INVENTORY_WRITE"
     */
    Optional<Permission> findByName(String name);
}

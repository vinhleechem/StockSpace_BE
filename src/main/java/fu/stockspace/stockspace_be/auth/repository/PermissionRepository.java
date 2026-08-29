package fu.stockspace.stockspace_be.auth.repository;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;




@Repository
public interface PermissionRepository extends JpaRepository<Permission, java.util.UUID> {





    Optional<Permission> findByName(String name);
}

package fu.stockspace.stockspace_be.common.repository;

import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

import java.util.UUID;

public interface SystemPolicyRepository extends JpaRepository<SystemPolicy, UUID> {




    Optional<SystemPolicy> findFirstByIsActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc();




    Optional<SystemPolicy> findFirstByVersionAndIsDeletedFalse(String version);




    @Query("SELECT p FROM SystemPolicy p WHERE p.isActive = true AND p.isDeleted = false")
    List<SystemPolicy> findAllActivePolicies();




    @Query("SELECT p FROM SystemPolicy p WHERE p.isDeleted = false")
    Page<SystemPolicy> findAllPolicies(Pageable pageable);
}

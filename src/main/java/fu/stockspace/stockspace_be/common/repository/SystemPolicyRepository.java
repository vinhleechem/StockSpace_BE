package fu.stockspace.stockspace_be.common.repository;

import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface SystemPolicyRepository extends JpaRepository<SystemPolicy, Long> {
    
    /**
     * Tìm chính sách hệ thống đang hiệu lực và mới nhất theo thời gian tạo.
     */
    Optional<SystemPolicy> findFirstByIsActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc();

    /**
     * Tìm chính sách theo phiên bản.
     */
    Optional<SystemPolicy> findFirstByVersionAndIsDeletedFalse(String version);

    /**
     * Lấy toàn bộ chính sách đang active.
     */
    @Query("SELECT p FROM SystemPolicy p WHERE p.isActive = true AND p.isDeleted = false")
    List<SystemPolicy> findAllActivePolicies();

    /**
     * Lấy danh sách tất cả chính sách chưa xóa.
     */
    @Query("SELECT p FROM SystemPolicy p WHERE p.isDeleted = false")
    Page<SystemPolicy> findAllPolicies(Pageable pageable);
}

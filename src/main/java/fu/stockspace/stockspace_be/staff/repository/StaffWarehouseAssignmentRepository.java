package fu.stockspace.stockspace_be.staff.repository;

import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StaffWarehouseAssignmentRepository extends JpaRepository<StaffWarehouseAssignment, UUID> {

    /**
     * Tìm danh sách các phân công kho đang ACTIVE của Staff dưới 1 Tenant.
     */
    @Query("""
            SELECT a FROM StaffWarehouseAssignment a
            WHERE a.staff.id = :staffId
              AND a.tenant.id = :tenantId
              AND a.status = :status
              AND a.isActive = true
              AND a.isDeleted = false
            """)
    List<StaffWarehouseAssignment> findByStaffIdAndTenantIdAndStatus(
            @Param("staffId") UUID staffId,
            @Param("tenantId") UUID tenantId,
            @Param("status") AssignmentStatus status);

    /**
     * Tìm tất cả phân công kho đang ACTIVE của Tenant (tất cả staff).
     */
    List<StaffWarehouseAssignment> findByTenantIdAndStatus(UUID tenantId, AssignmentStatus status);

    /**
     * Kiểm tra Staff có phân công ACTIVE tại 1 kho cụ thể hay không.
     */
    boolean existsByStaffIdAndWarehouseIdAndStatus(UUID staffId, UUID warehouseId, AssignmentStatus status);

    /**
     * Kiểm tra assignment ACTIVE của Staff tại một warehouse cụ thể trong đúng Tenant.
     */
    @Query("""
            SELECT COUNT(a) > 0 FROM StaffWarehouseAssignment a
            WHERE a.staff.id = :staffId
              AND a.tenant.id = :tenantId
              AND a.warehouse.id = :warehouseId
              AND a.status = :status
              AND a.isActive = true
              AND a.isDeleted = false
            """)
    boolean existsActiveByStaffAndTenantAndWarehouse(
            @Param("staffId") UUID staffId,
            @Param("tenantId") UUID tenantId,
            @Param("warehouseId") UUID warehouseId,
            @Param("status") AssignmentStatus status);

    /**
     * Lấy toàn bộ lịch sử phân công kho của Staff trong 1 Tenant (mới nhất lên đầu).
     */
    List<StaffWarehouseAssignment> findByTenantIdAndStaffIdOrderByStartDateDesc(UUID tenantId, UUID staffId);

    /**
     * Lấy toàn bộ lịch sử phân công kho sự nghiệp của Staff qua tất cả các Tenant (mới nhất lên đầu).
     */
    @Query("SELECT a FROM StaffWarehouseAssignment a WHERE a.staff.id = :staffId ORDER BY a.startDate DESC")
    List<StaffWarehouseAssignment> findAllCareerAssignmentsByStaffId(@Param("staffId") UUID staffId);
}

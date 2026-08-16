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




    List<StaffWarehouseAssignment> findByTenantIdAndStatus(UUID tenantId, AssignmentStatus status);

    List<StaffWarehouseAssignment> findByTenantIdAndWarehouseIdAndStatus(
            UUID tenantId, UUID warehouseId, AssignmentStatus status);




    boolean existsByStaffIdAndWarehouseIdAndStatus(UUID staffId, UUID warehouseId, AssignmentStatus status);




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

    @Query("""
            SELECT a FROM StaffWarehouseAssignment a
            JOIN FETCH a.staff
            WHERE a.warehouse.id = :warehouseId
              AND a.status = fu.stockspace.stockspace_be.staff.entity.AssignmentStatus.ACTIVE
              AND a.isActive = true
              AND a.isDeleted = false
            ORDER BY a.startDate ASC
            """)
    List<StaffWarehouseAssignment> findActiveByWarehouseId(
            @Param("warehouseId") UUID warehouseId);




    List<StaffWarehouseAssignment> findByTenantIdAndStaffIdOrderByStartDateDesc(UUID tenantId, UUID staffId);




    @Query("SELECT a FROM StaffWarehouseAssignment a WHERE a.staff.id = :staffId ORDER BY a.startDate DESC")
    List<StaffWarehouseAssignment> findAllCareerAssignmentsByStaffId(@Param("staffId") UUID staffId);
}

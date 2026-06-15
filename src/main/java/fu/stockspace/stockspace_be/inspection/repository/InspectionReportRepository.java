package fu.stockspace.stockspace_be.inspection.repository;

import fu.stockspace.stockspace_be.inspection.entity.InspectionReport;
import fu.stockspace.stockspace_be.inspection.entity.InspectionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


public interface InspectionReportRepository extends JpaRepository<InspectionReport, Long> {

    List<InspectionReport> findByWarehouseId(Long warehouseId);

    Page<InspectionReport> findByInspectorId(Long inspectorId, Pageable pageable);

    Page<InspectionReport> findByStatus(InspectionStatus status, Pageable pageable);

    @Query("""
            SELECT r FROM InspectionReport r
            WHERE r.warehouse.owner.id = :ownerId
            ORDER BY r.createdAt DESC
            """)
    Page<InspectionReport> findByWarehouseOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("""
            SELECT r FROM InspectionReport r
            WHERE (:status IS NULL OR r.status = :status)
            ORDER BY r.createdAt DESC
            """)
    Page<InspectionReport> findAllWithFilter(@Param("status") InspectionStatus status, Pageable pageable);
}

package fu.stockspace.stockspace_be.inspection.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

/**
 * Entity InspectionReport — báo cáo kiểm định kho.
 * Map với bảng: inspection_reports
 *
 * Luồng:
 *   Owner request → PENDING
 *   Admin gán Inspector → IN_PROGRESS
 *   Inspector nộp báo cáo → PASSED (verify kho) | FAILED
 */
@Entity
@Table(name = "inspection_reports", indexes = {
        @Index(name = "idx_inspection_reports_warehouse_id",  columnList = "warehouse_id"),
        @Index(name = "idx_inspection_reports_inspector_id",  columnList = "inspector_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InspectionReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** null cho đến khi Admin gán Inspector */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inspector_id")
    private User inspector;

    /**
     * Dữ liệu checklist kiểm định — lưu dạng JSON string.
     * Ví dụ: {"fireExtinguisher": true, "electricalSafety": false, ...}
     */
    @Column(name = "checklist_data", columnDefinition = "TEXT")
    private String checklistData;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InspectionStatus status = InspectionStatus.PENDING;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "inspected_at")
    private LocalDateTime inspectedAt;
}

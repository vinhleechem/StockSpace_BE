package fu.stockspace.stockspace_be.staff.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;





@Entity
@Table(
    name = "staff_warehouse_assignments",
    indexes = {
        @Index(name = "idx_swa_staff_id",     columnList = "staff_id"),
        @Index(name = "idx_swa_tenant_id",    columnList = "tenant_id"),
        @Index(name = "idx_swa_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_swa_status",       columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StaffWarehouseAssignment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;


    @Column(name = "custom_title", length = 150)
    private String customTitle;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;


    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;


    @Column(name = "end_date")
    private LocalDateTime endDate;


    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ACTIVE;


    @Column(name = "notes", length = 500)
    private String notes;
}

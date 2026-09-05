package fu.stockspace.stockspace_be.wms.stock.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_audits", indexes = {
        @Index(name = "idx_inventory_audits_warehouse_id", columnList = "warehouse_id"),
        @Index(name = "idx_inventory_audits_requested_by", columnList = "requested_by"),
        @Index(name = "idx_inventory_audits_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InventoryAudit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Immutable tenant owner. Nullable only for legacy rows created before v2. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", length = 20)
    @Builder.Default
    private AuditScopeType scopeType = AuditScopeType.WAREHOUSE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_rack_id")
    private WarehouseRack scopeRack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scope_bin_id")
    private WarehouseBin scopeBin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AuditStatus status = AuditStatus.PENDING;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Column(name = "workflow_version", nullable = false)
    @Builder.Default
    private int workflowVersion = 1;

    @Column(name = "count_round", nullable = false)
    @Builder.Default
    private int countRound = 1;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "review_reason", columnDefinition = "text")
    private String reviewReason;

    @Version
    @Column(name = "version")
    private Long version;
}

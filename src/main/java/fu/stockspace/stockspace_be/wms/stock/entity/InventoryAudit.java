package fu.stockspace.stockspace_be.wms.stock.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AuditStatus status = AuditStatus.PENDING;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}

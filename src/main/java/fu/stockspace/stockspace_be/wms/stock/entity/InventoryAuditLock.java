package fu.stockspace.stockspace_be.wms.stock.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/** Movement lock held while a v2 audit is actively counting. */
@Entity
@Table(name = "inventory_audit_locks", indexes = {
        @Index(name = "idx_inventory_audit_locks_warehouse", columnList = "warehouse_id"),
        @Index(name = "idx_inventory_audit_locks_audit", columnList = "audit_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InventoryAuditLock extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id", nullable = false)
    private InventoryAudit audit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;
}

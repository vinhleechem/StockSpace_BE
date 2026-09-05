package fu.stockspace.stockspace_be.wms.stock.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.wms.receipt.entity.InventoryReceipt;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/** Idempotency and traceability record for one batch-level delta of an approved audit item. */
@Entity
@Table(name = "inventory_audit_adjustments", uniqueConstraints = {
        @UniqueConstraint(name = "ux_inventory_audit_adjustment_item_batch", columnNames = {"audit_item_id", "batch_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InventoryAuditAdjustment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id", nullable = false)
    private InventoryAudit audit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_item_id", nullable = false)
    private InventoryAuditItem auditItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_id", nullable = false)
    private InventoryReceipt receipt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private StockBatch batch;

    @Column(name = "delta", nullable = false)
    private int delta;
}

package fu.stockspace.stockspace_be.wms.stock.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseBin;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseRack;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "inventory_audit_items", indexes = {
        @Index(name = "idx_inventory_audit_items_audit_id", columnList = "audit_id"),
        @Index(name = "idx_inventory_audit_items_batch_id", columnList = "batch_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InventoryAuditItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id", nullable = false)
    private InventoryAudit audit;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private StockBatch batch;

    /** Used by v2 for a counted SKU that was not present in the system snapshot. */
    @Column(name = "sku_id")
    private UUID skuId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rack_id")
    private WarehouseRack rack;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bin_id")
    private WarehouseBin bin;


    @Column(name = "expected_quantity", nullable = false)
    private int expectedQuantity;


    @Column(name = "actual_quantity")
    private Integer actualQuantity;






    @Column(name = "discrepancy")
    private Integer discrepancy;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_status", nullable = false, length = 20)
    @Builder.Default
    private AuditCountStatus countStatus = AuditCountStatus.UNCOUNTED;

    @Column(name = "count_round", nullable = false)
    @Builder.Default
    private int countRound = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counted_by")
    private User countedBy;

    @Column(name = "counted_at")
    private java.time.LocalDateTime countedAt;

    @Column(name = "variance_reason", columnDefinition = "text")
    private String varianceReason;
}

package fu.stockspace.stockspace_be.wms.stock.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
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
    @JoinColumn(name = "batch_id", nullable = false)
    private StockBatch batch;

    /** Số lượng hệ thống ghi nhận tại thời điểm tạo phiếu kiểm kê */
    @Column(name = "expected_quantity", nullable = false)
    private int expectedQuantity;

    /** Số lượng thực đếm được — được điền khi submitAudit */
    @Column(name = "actual_quantity")
    private Integer actualQuantity;

    /**
     * Chênh lệch: actualQuantity - expectedQuantity
     * Âm = thiếu, Dương = thừa.
     * Tính toán và lưu lại khi submit.
     */
    @Column(name = "discrepancy")
    private Integer discrepancy;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}

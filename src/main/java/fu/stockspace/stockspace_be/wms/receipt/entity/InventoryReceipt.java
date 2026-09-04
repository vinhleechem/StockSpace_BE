package fu.stockspace.stockspace_be.wms.receipt.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Table(name = "inventory_receipts", indexes = {
        @Index(name = "idx_inventory_receipts_tenant_warehouse", columnList = "tenant_id,warehouse_id"),
        @Index(name = "idx_inventory_receipts_tenant_status", columnList = "tenant_id,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class InventoryReceipt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Immutable data owner. This must not be inferred from createdBy when a
     * receipt is read because staff membership can change over time.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, updatable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private DocumentType type;

    @Column(name = "signature_data", columnDefinition = "text")
    private String signatureData;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(name = "receiver_name", length = 255)
    private String receiverName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;
}

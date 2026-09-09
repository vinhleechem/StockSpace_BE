package fu.stockspace.stockspace_be.wms.transfer.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A physical movement leg.  One transfer request can have an outbound leg,
 * several forward/retry legs and one or more return legs.  The original
 * transfer document is deliberately kept immutable while legs hold the
 * operational route history.
 */
@Entity
@Table(name = "stock_transfer_attempts", indexes = {
        @Index(name = "idx_stock_transfer_attempts_transfer_sequence", columnList = "transfer_id,sequence_no")
}, uniqueConstraints = {
        @UniqueConstraint(name = "ux_stock_transfer_attempts_transfer_sequence",
                columnNames = {"transfer_id", "sequence_no"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StockTransferAttempt extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private StockTransfer transfer;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private StockTransferAttemptType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    @Builder.Default
    private StockTransferAttemptStatus status = StockTransferAttemptStatus.PLANNED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_warehouse_id", nullable = false)
    private Warehouse sourceWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Column(name = "planned_quantity", nullable = false)
    private int plannedQuantity;

    @Column(name = "shipped_quantity", nullable = false)
    @Builder.Default
    private int shippedQuantity = 0;

    @Column(name = "received_quantity", nullable = false)
    @Builder.Default
    private int receivedQuantity = 0;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}

package fu.stockspace.stockspace_be.booking.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Entity BookingRequest — yêu cầu thuê kho từ Tenant.
 * Map với bảng: booking_requests
 *
 * Luồng:
 *   Tenant tạo → PENDING
 *   Owner approve → APPROVED → tạo RentalContract + deduct deposit
 *   Owner reject  → REJECTED
 *   Tenant cancel → REJECTED (nếu còn PENDING)
 */
@Entity
@Table(name = "booking_requests", indexes = {
        @Index(name = "idx_booking_requests_tenant_id",    columnList = "tenant_id"),
        @Index(name = "idx_booking_requests_warehouse_id", columnList = "warehouse_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BookingRequest extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ==================== Relations ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // ==================== Fields ====================

    @Column(name = "deposit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    /** Lý do từ chối (nếu REJECTED) */
    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;
}

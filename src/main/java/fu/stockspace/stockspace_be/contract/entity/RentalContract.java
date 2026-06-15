package fu.stockspace.stockspace_be.contract.entity;

import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;

/**
 * Entity RentalContract — hợp đồng thuê kho được tạo tự động khi Owner approve Booking.
 * Map với bảng: rental_contracts
 *
 * Cơ chế bàn giao (handover):
 *   - ownerConfirmed + tenantConfirmed = true → status → COMPLETED
 *   - warehouse status → AVAILABLE
 */
@Entity
@Table(name = "rental_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RentalContract extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** 1-1 với BookingRequest */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", unique = true, nullable = false)
    private BookingRequest booking;

    // ==================== Xác nhận bàn giao ====================

    @Column(name = "tenant_confirmed", nullable = false)
    @Builder.Default
    private boolean tenantConfirmed = false;

    @Column(name = "owner_confirmed", nullable = false)
    @Builder.Default
    private boolean ownerConfirmed = false;

    // ==================== Thông tin hợp đồng ====================

    /** URL ảnh hợp đồng giấy (JSON array dạng String) */
    @Column(name = "paper_contract_images", columnDefinition = "TEXT")
    private String paperContractImages;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ContractStatus status = ContractStatus.ACTIVE;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
}

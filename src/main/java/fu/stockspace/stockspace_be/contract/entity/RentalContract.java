package fu.stockspace.stockspace_be.contract.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.UUID;









@Entity
@Table(name = "rental_contracts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RentalContract extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", nullable = false, length = 40)
    private RentalPricingType pricingType;

    @Column(name = "rental_price_snapshot", precision = 15, scale = 2)
    private java.math.BigDecimal rentalPriceSnapshot;

    @Column(name = "final_monthly_rent", nullable = false, precision = 15, scale = 2)
    private java.math.BigDecimal finalMonthlyRent;

    @Column(name = "leased_width", nullable = false, precision = 15, scale = 6)
    private java.math.BigDecimal leasedWidth;

    @Column(name = "leased_length", nullable = false, precision = 15, scale = 6)
    private java.math.BigDecimal leasedLength;

    @Column(name = "leased_height", nullable = false, precision = 15, scale = 6)
    private java.math.BigDecimal leasedHeight;

    @Column(name = "leased_area_m2", nullable = false, precision = 15, scale = 6)
    private java.math.BigDecimal leasedAreaM2;

    @Column(name = "owner_note", columnDefinition = "TEXT")
    private String ownerNote;


    @Column(name = "paper_contract_files", columnDefinition = "TEXT")
    private String paperContractFiles;

    @Column(name = "layout_snapshot", nullable = false, columnDefinition = "TEXT")
    private String layoutSnapshot;

    @Column(name = "change_request_reason", columnDefinition = "TEXT")
    private String changeRequestReason;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ContractStatus status = ContractStatus.DRAFT;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "expiry_reminder_sent", nullable = false)
    @Builder.Default
    private boolean expiryReminderSent = false;

}

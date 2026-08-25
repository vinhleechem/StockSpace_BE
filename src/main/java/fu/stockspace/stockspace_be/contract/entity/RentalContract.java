package fu.stockspace.stockspace_be.contract.entity;

import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
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


    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", unique = true)
    private BookingRequest booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_type", length = 40)
    private RentalPricingType pricingType;

    @Column(name = "rental_price_snapshot", precision = 15, scale = 2)
    private java.math.BigDecimal rentalPriceSnapshot;

    @Column(name = "final_monthly_rent", precision = 15, scale = 2)
    private java.math.BigDecimal finalMonthlyRent;

    @Column(name = "leased_width", precision = 15, scale = 6)
    private java.math.BigDecimal leasedWidth;

    @Column(name = "leased_length", precision = 15, scale = 6)
    private java.math.BigDecimal leasedLength;

    @Column(name = "leased_height", precision = 15, scale = 6)
    private java.math.BigDecimal leasedHeight;

    @Column(name = "leased_area_m2", precision = 15, scale = 6)
    private java.math.BigDecimal leasedAreaM2;


    @Column(name = "tenant_confirmed", nullable = false)
    @Builder.Default
    private boolean tenantConfirmed = false;

    @Column(name = "owner_confirmed", nullable = false)
    @Builder.Default
    private boolean ownerConfirmed = false;




    @Column(name = "paper_contract_files", columnDefinition = "TEXT")
    private String paperContractFiles;

    /** @deprecated Use paperContractFiles. */
    @Transient
    @Deprecated
    private String paperContractImages;

    @Column(name = "layout_snapshot", columnDefinition = "TEXT")
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
    private ContractStatus status = ContractStatus.UNDER_NEGOTIATION;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "cancel_reason", columnDefinition = "TEXT")
    private String cancelReason;

    @Column(name = "cancel_evidence", columnDefinition = "TEXT")
    private String cancelEvidence;

    @Column(name = "expiry_reminder_sent", nullable = false)
    @Builder.Default
    private boolean expiryReminderSent = false;

    @Deprecated
    public String getPaperContractImages() {
        return paperContractFiles != null ? paperContractFiles : paperContractImages;
    }

    @Deprecated
    public void setPaperContractImages(String paperContractImages) {
        this.paperContractImages = paperContractImages;
        if (this.paperContractFiles == null) {
            this.paperContractFiles = paperContractImages;
        }
    }

    /** Returns direct owner first, then resolves the legacy booking relation. */
    @Transient
    public User getEffectiveOwner() {
        if (owner != null) {
            return owner;
        }
        return booking != null && booking.getWarehouse() != null
                ? booking.getWarehouse().getOwner()
                : null;
    }

    /** Returns direct tenant first, then resolves the legacy booking relation. */
    @Transient
    public User getEffectiveTenant() {
        if (tenant != null) {
            return tenant;
        }
        return booking != null ? booking.getTenant() : null;
    }

    /** Returns direct warehouse first, then resolves the legacy booking relation. */
    @Transient
    public Warehouse getEffectiveWarehouse() {
        if (warehouse != null) {
            return warehouse;
        }
        return booking != null ? booking.getWarehouse() : null;
    }
}

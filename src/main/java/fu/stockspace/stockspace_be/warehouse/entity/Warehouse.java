package fu.stockspace.stockspace_be.warehouse.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import java.util.UUID;










@Entity
@Table(name = "warehouses", indexes = {
        @Index(name = "idx_warehouses_owner_id", columnList = "owner_id"),
        @Index(name = "idx_warehouses_status",   columnList = "status"),
        @Index(name = "idx_warehouses_visible_until", columnList = "visible_until")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Warehouse extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;



    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    private WarehouseType type;



    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;


    @Column(name = "capacity", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_pricing_type", nullable = false, length = 40)
    @Builder.Default
    private RentalPricingType rentalPricingType = RentalPricingType.FIXED_MONTHLY;

    @Column(name = "rental_price", precision = 15, scale = 2)
    private BigDecimal rentalPrice;

    /**
     * Transitional source compatibility for code that still uses the old
     * field name. This field is not persisted; the migration keeps the old
     * database column while rental_price becomes the canonical value.
     */
    @Transient
    @Deprecated
    private BigDecimal pricePerMonth;

    @Deprecated
    public BigDecimal getPricePerMonth() {
        return rentalPrice != null ? rentalPrice : pricePerMonth;
    }

    @Deprecated
    public void setPricePerMonth(BigDecimal pricePerMonth) {
        this.pricePerMonth = pricePerMonth;
        if (this.rentalPrice == null) {
            this.rentalPrice = pricePerMonth;
        }
    }




    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private WarehouseStatus status = WarehouseStatus.PENDING_APPROVAL;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "visible_until")
    private LocalDateTime visibleUntil;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_version_id", nullable = false)
    private SystemPolicy policy;



    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<WarehouseImage> images = new ArrayList<>();
}

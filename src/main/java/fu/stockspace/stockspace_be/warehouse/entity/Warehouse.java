package fu.stockspace.stockspace_be.warehouse.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity Warehouse — đại diện cho một kho bãi được đăng trên hệ thống.
 * Map với bảng: warehouses
 *
 * Luồng trạng thái:
 *   Owner tạo → PENDING_VERIFICATION → Admin/Inspector duyệt → AVAILABLE
 *   Tenant thuê → RENTED → Hợp đồng kết thúc → AVAILABLE
 */
@Entity
@Table(name = "warehouses", indexes = {
        @Index(name = "idx_warehouses_owner_id", columnList = "owner_id"),
        @Index(name = "idx_warehouses_status",   columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Warehouse extends BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // ==================== Relations ====================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_id", nullable = false)
    private WarehouseType type;

    // ==================== Basic Info ====================

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /** Diện tích / sức chứa (m²) */
    @Column(name = "capacity", nullable = false, precision = 10, scale = 2)
    private BigDecimal capacity;

    @Column(name = "price_per_month", nullable = false, precision = 15, scale = 2)
    private BigDecimal pricePerMonth;

    // ==================== Status ====================

    /** true nếu đã qua kiểm định / Admin duyệt */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private WarehouseStatus status = WarehouseStatus.PENDING_VERIFICATION;

    // ==================== Images ====================

    @OneToMany(mappedBy = "warehouse", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<WarehouseImage> images = new ArrayList<>();
}

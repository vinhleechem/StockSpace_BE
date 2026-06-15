package fu.stockspace.stockspace_be.warehouse.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

/**
 * Entity WarehouseZone — khu vực trong layout kho (Zone A, Zone B, v.v.)
 * Map với bảng: warehouse_zones
 */
@Entity
@Table(name = "warehouse_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WarehouseZone extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_id", nullable = false)
    private WarehouseLayout layout;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "max_weight", precision = 10, scale = 2)
    private BigDecimal maxWeight;

    @Column(name = "max_volume", precision = 10, scale = 2)
    private BigDecimal maxVolume;

    // ==================== Tọa độ & kích thước trên bản đồ ====================

    @Column(name = "coordinate_x")
    private Integer coordinateX;

    @Column(name = "coordinate_y")
    private Integer coordinateY;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;
}

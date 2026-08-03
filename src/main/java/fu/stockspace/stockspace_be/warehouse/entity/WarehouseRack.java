package fu.stockspace.stockspace_be.warehouse.entity;

import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;

import java.util.UUID;

/**
 * Entity WarehouseRack — kệ hàng trong một Zone.
 * Map với bảng: warehouse_racks
 */
@Entity
@Table(name = "warehouse_racks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WarehouseRack extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_id", nullable = false)
    private WarehouseLayout layout;

    @Column(name = "zone_name", length = 100)
    private String zoneName;

    @Column(name = "zone_code", length = 50)
    private String zoneCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "max_weight", precision = 10, scale = 2)
    private BigDecimal maxWeight;

    @Column(name = "max_volume", precision = 10, scale = 2)
    private BigDecimal maxVolume;

    @Column(name = "coordinate_x")
    private Integer coordinateX;

    @Column(name = "coordinate_y")
    private Integer coordinateY;

    @Column(name = "position_z")
    @Builder.Default
    private Integer positionZ = 0;

    @Column(name = "rotation")
    @Builder.Default
    private Integer rotation = 0;

    @Column(name = "width")
    private Integer width;

    @Column(name = "length")
    private Integer length;

    @Column(name = "height")
    private Integer height;
}

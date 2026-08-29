package fu.stockspace.stockspace_be.warehouse.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


import java.util.UUID;
import java.math.BigDecimal;







@Entity
@Table(name = "warehouse_layouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WarehouseLayout extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private User tenant;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = true;

    @Column(name = "width", nullable = false)
    @Builder.Default
    private BigDecimal width = new BigDecimal("100.000000");

    @Column(name = "length", nullable = false)
    @Builder.Default
    private BigDecimal length = new BigDecimal("100.000000");

    @Column(name = "height", nullable = false)
    @Builder.Default
    private BigDecimal height = new BigDecimal("100.000000");


    @Column(name = "positions", columnDefinition = "TEXT")
    private String positions;
}

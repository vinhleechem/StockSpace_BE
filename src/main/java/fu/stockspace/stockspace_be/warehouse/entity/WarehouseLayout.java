package fu.stockspace.stockspace_be.warehouse.entity;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


/**
 * Entity WarehouseLayout — bản đồ layout kho, có thể là layout mặc định của Owner
 * hoặc layout tuỳ chỉnh của Tenant sau khi thuê.
 * Map với bảng: warehouse_layouts
 */
@Entity
@Table(name = "warehouse_layouts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class WarehouseLayout extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** null = layout mặc định của Owner; có giá trị = layout riêng của Tenant */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private User tenant;

    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private boolean isDefault = true;
}

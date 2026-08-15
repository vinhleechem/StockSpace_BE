package fu.stockspace.stockspace_be.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;


import java.util.UUID;








@Entity
@Table(name = "warehouse_images", indexes = {
        @Index(name = "idx_warehouse_images_warehouse_id", columnList = "warehouse_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;


    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;


    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}

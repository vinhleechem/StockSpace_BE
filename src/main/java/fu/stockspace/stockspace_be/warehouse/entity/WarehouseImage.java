package fu.stockspace.stockspace_be.warehouse.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

/**
 * Entity WarehouseImage — lưu URL ảnh của một Warehouse.
 * Map với bảng: warehouse_images
 *
 * Quan hệ: N ảnh → 1 Warehouse (@ManyToOne)
 */
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
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** URL đầy đủ của ảnh (S3, Cloudinary, v.v.) */
    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    /** Thứ tự hiển thị — ảnh đầu tiên (0) là ảnh bìa */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}

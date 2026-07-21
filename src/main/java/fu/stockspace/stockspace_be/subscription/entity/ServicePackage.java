package fu.stockspace.stockspace_be.subscription.entity;
import fu.stockspace.stockspace_be.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
/**
 * Entity ServicePackage - Gói dịch vụ sử dụng nền tảng StockSpace.
 */
@Entity
@Table(name = "service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ServicePackage extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private java.util.UUID id;
    @Column(name = "name", nullable = false, unique = true, length = 150)
    private String name;
    /** Danh sách tính năng (lưu trữ dưới dạng chuỗi JSON hoặc TEXT mô tả) */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;
    @Column(name = "price", nullable = false, precision = 15, scale = 2)
    private BigDecimal price;
    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;
    /**
     * Số lượng nhân viên tối đa cho phép (Staff quota).
     * 0 = không giới hạn (dùng cho gói Enterprise/custom).
     * Mặc định = 0 (cho gói cũ chưa set).
     */
    @Column(name = "max_staff", nullable = false)
    @Builder.Default
    private int maxStaff = 0;
}
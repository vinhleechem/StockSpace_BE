package fu.stockspace.stockspace_be.subscription.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.*;
import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePackageRequest {
    private String name;
    private String features;
    @DecimalMin(value = "0.00", message = "Giá gói dịch vụ không được nhỏ hơn 0")
    private BigDecimal price;
    @Min(value = 1, message = "Thời hạn gói tối thiểu là 1 ngày")
    private Integer durationDays;
}

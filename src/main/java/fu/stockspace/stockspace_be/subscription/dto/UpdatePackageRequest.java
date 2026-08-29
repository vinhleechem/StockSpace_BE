package fu.stockspace.stockspace_be.subscription.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
    @DecimalMax(value = "9999999999999.99", message = "Price exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal price;
    @Min(value = 1, message = "Thời hạn gói tối thiểu là 1 ngày")
    private Integer durationDays;
    @Min(value = 0, message = "maxStaff không được âm")
    private Integer maxStaff;
}


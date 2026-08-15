package fu.stockspace.stockspace_be.subscription.dto;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePackageRequest {
    @NotBlank(message = "Tên gói dịch vụ không được để trống")
    private String name;
    private String features;
    @NotNull(message = "Giá gói dịch vụ không được để trống")
    @DecimalMin(value = "0.00", message = "Giá gói dịch vụ không được nhỏ hơn 0")
    private BigDecimal price;
    @NotNull(message = "Thời hạn gói không được để trống")
    @Min(value = 1, message = "Thời hạn gói tối thiểu là 1 ngày")
    private Integer durationDays;

    @Min(value = 0, message = "maxStaff không được âm")
    @Builder.Default
    private int maxStaff = 0;
}


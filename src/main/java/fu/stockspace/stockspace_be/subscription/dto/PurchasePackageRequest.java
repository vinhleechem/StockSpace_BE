package fu.stockspace.stockspace_be.subscription.dto;
import jakarta.validation.constraints.NotNull;
import lombok.*;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchasePackageRequest {
    @NotNull(message = "ID gói dịch vụ không được để trống")
    private Integer packageId;
}

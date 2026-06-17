package fu.stockspace.stockspace_be.subscription.dto;
import lombok.*;
import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePackageResponse {
    private Integer id;
    private String name;
    private String features;
    private BigDecimal price;
    private Integer durationDays;
}
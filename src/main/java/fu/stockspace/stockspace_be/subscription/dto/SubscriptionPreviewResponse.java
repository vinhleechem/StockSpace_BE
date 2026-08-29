package fu.stockspace.stockspace_be.subscription.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPreviewResponse {

    private UUID currentPackageId;
    private String currentPackageName;
    private int currentMaxStaff;
    private BigDecimal currentPrice;

    private UUID newPackageId;
    private String newPackageName;
    private int newMaxStaff;
    private BigDecimal newPrice;


    private String transactionType;


    private boolean canProceed;


    private String message;
}

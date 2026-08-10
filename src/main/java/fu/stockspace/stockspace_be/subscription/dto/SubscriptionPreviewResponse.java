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

    /** Loại giao dịch: NEW_PURCHASE, RENEWAL, UPGRADE, DOWNGRADE_BLOCKED */
    private String transactionType;

    /** Cho phép thực hiện mua hay không */
    private boolean canProceed;

    /** Thông điệp giải thích nghiệp vụ cho FE hiển thị */
    private String message;
}

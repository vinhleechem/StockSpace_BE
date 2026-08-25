package fu.stockspace.stockspace_be.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateRentalContractRequest {

    @NotNull(message = "Warehouse is required")
    private UUID warehouseId;

    @NotBlank(message = "Tenant email is required")
    @Email(message = "Tenant email must be valid")
    private String tenantEmail;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotNull(message = "Leased width is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Leased width must be greater than 0")
    private BigDecimal leasedWidth;

    @NotNull(message = "Leased length is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Leased length must be greater than 0")
    private BigDecimal leasedLength;

    @NotNull(message = "Leased height is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Leased height must be greater than 0")
    private BigDecimal leasedHeight;

    private BigDecimal negotiatedMonthlyRent;

    @Size(max = 2000, message = "Owner note must not exceed 2000 characters")
    private String ownerNote;

    private List<String> paperContractFiles;
}

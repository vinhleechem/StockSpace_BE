package fu.stockspace.stockspace_be.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Owner request used to preview or create a direct rental-contract draft")
public class CreateRentalContractRequest {

    @Schema(description = "Owner's verified warehouse", format = "uuid")
    @NotNull(message = "Warehouse is required")
    private UUID warehouseId;

    @Schema(description = "Email of an active ROLE_TENANT account", example = "tenant@example.com")
    @NotBlank(message = "Tenant email is required")
    @Email(message = "Tenant email must be valid")
    private String tenantEmail;

    @Schema(example = "2026-09-01")
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @Schema(example = "2027-08-31", description = "Inclusive end date; rental duration must be at least 7 days")
    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @Schema(example = "10", description = "Leased width in metres")
    @NotNull(message = "Leased width is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Leased width must be greater than 0")
    private BigDecimal leasedWidth;

    @Schema(example = "8", description = "Leased length in metres")
    @NotNull(message = "Leased length is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Leased length must be greater than 0")
    private BigDecimal leasedLength;

    @Schema(example = "4", description = "Leased height in metres")
    @NotNull(message = "Leased height is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Leased height must be greater than 0")
    private BigDecimal leasedHeight;

    @Schema(description = "Required only for NEGOTIATED warehouses; omit for fixed or per-m² pricing", example = "12000000")
    private BigDecimal negotiatedMonthlyRent;

    @Schema(example = "Terms agreed outside the platform")
    @Size(max = 2000, message = "Owner note must not exceed 2000 characters")
    private String ownerNote;

    @Schema(description = "Paper-contract file URLs. At least one non-blank URL is required before submit")
    private List<String> paperContractFiles;
}

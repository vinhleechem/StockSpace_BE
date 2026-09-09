package fu.stockspace.stockspace_be.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Schema(description = "Owner request used to create a renewal contract draft")
public class CreateContractRenewalRequest {

    @NotNull(message = "End date is required")
    @Schema(example = "2028-08-31", description = "Inclusive end date for the renewal")
    private LocalDate endDate;

    @DecimalMin(value = "0.0", inclusive = false,
            message = "Negotiated monthly rent must be greater than 0")
    @Schema(description = "Required only when the current warehouse pricing type is NEGOTIATED",
            example = "12000000")
    private BigDecimal negotiatedMonthlyRent;

    @Size(max = 2000, message = "Owner note must not exceed 2000 characters")
    @Schema(example = "Renewal terms agreed outside StockSpace")
    private String ownerNote;

    @Schema(description = "Paper-contract file URLs; required before submission")
    private List<String> paperContractFiles;
}

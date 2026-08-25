package fu.stockspace.stockspace_be.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Owner-editable terms for a direct rental contract draft.
 *
 * Contract participants and the warehouse are intentionally not part of this
 * request. They are immutable after the draft has been created.
 */
@Getter
@Setter
public class UpdateRentalContractRequest {

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

    /** A null value keeps the files already attached to the draft. */
    private List<String> paperContractFiles;
}

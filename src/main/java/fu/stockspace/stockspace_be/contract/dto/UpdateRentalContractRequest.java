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

/**
 * Owner-editable terms for a direct rental contract draft.
 *
 * Contract participants and the warehouse are intentionally not part of this
 * request. They are immutable after the draft has been created.
 */
@Getter
@Setter
@Schema(description = "Owner-editable terms while a contract is DRAFT or CHANGES_REQUESTED; participants and warehouse are immutable")
public class UpdateRentalContractRequest {

    @Schema(example = "2026-09-01")
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @Schema(example = "2027-08-31")
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

    @Schema(description = "Required only for NEGOTIATED warehouses", example = "12000000")
    private BigDecimal negotiatedMonthlyRent;

    @Schema(example = "Updated after tenant feedback")
    @Size(max = 2000, message = "Owner note must not exceed 2000 characters")
    private String ownerNote;

    /** A null value keeps the files already attached to the draft. */
    @Schema(description = "Replacement paper-contract file URLs; null preserves existing files")
    private List<String> paperContractFiles;
}

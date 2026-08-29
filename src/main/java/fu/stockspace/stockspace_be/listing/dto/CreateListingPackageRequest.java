package fu.stockspace.stockspace_be.listing.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateListingPackageRequest {

    @NotBlank(message = "Listing package name is required")
    @Size(max = 150, message = "Listing package name must not exceed 150 characters")
    private String name;

    @NotNull(message = "Listing package duration is required")
    @Min(value = 1, message = "Listing package duration must be positive")
    private Integer durationDays;

    @NotNull(message = "Listing package price is required")
    @DecimalMin(value = "0.00", message = "Listing package price must not be negative")
    @DecimalMax(value = "9999999999999.99", message = "Listing package price exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Listing package price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal price;
}

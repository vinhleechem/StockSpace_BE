package fu.stockspace.stockspace_be.warehouse.dto;

import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class UpdateWarehouseRequest {

    @Size(max = 255, message = "Warehouse name must not exceed 255 characters")
    private String name;

    private String address;

    private String description;

    @DecimalMin(value = "1.0", message = "Capacity must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "Capacity exceeds the supported limit")
    private BigDecimal capacity;

    @DecimalMin(value = "0.0", inclusive = false, message = "Rental price must be greater than 0")
    @DecimalMax(value = "9999999999999.99", message = "Rental price exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Rental price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal rentalPrice;

    /** @deprecated Use rentalPrice and rentalPricingType. */
    @Deprecated
    @DecimalMin(value = "0.0", inclusive = false, message = "Rental price must be greater than 0")
    @DecimalMax(value = "9999999999999.99", message = "Rental price exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Rental price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal pricePerMonth;

    private RentalPricingType rentalPricingType;

    private UUID typeId;
}

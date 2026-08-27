package fu.stockspace.stockspace_be.warehouse.dto;

import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Schema(description = "Warehouse creation payload; submitted as the JSON 'request' part of multipart/form-data")
public class CreateWarehouseRequest {

    @NotNull(message = "Warehouse type is required")
    private UUID typeId;

    @NotBlank(message = "Warehouse name is required")
    @Size(max = 255, message = "Warehouse name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "Address is required")
    private String address;

    @Size(max = 50, message = "Province code must not exceed 50 characters")
    private String provinceCode;

    @Size(max = 255, message = "Province name must not exceed 255 characters")
    private String provinceName;

    @Size(max = 50, message = "District code must not exceed 50 characters")
    private String districtCode;

    @Size(max = 255, message = "District name must not exceed 255 characters")
    private String districtName;

    private String description;

    @NotNull(message = "Capacity is required")
    @DecimalMin(value = "1.0", message = "Capacity must be greater than 0")
    @DecimalMax(value = "99999999.99", message = "Capacity exceeds the supported limit")
    private BigDecimal capacity;

    @Schema(description = "Required and positive for fixed/per-m² pricing; must be null for negotiated pricing", example = "100000000")
    @DecimalMin(value = "0.0", inclusive = false, message = "Rental price must be greater than 0")
    @DecimalMax(value = "9999999999999.99", message = "Rental price exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Rental price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal rentalPrice;

    @Schema(example = "FIXED_MONTHLY", allowableValues = {"FIXED_MONTHLY", "PER_SQUARE_METER_MONTHLY", "NEGOTIATED"})
    private RentalPricingType rentalPricingType;

    private List<String> imageUrls;
}

package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;





@Getter
@Setter
public class UpdateWarehouseRequest {

    @Size(max = 255, message = "Tên kho tối đa 255 ký tự")
    private String name;

    private String address;

    private String description;

    @DecimalMin(value = "1.0", message = "Sức chứa phải lớn hơn 0")
    @DecimalMax(value = "99999999.99", message = "Sức chứa tối đa là 99,999,999.99 m²")
    private BigDecimal capacity;


    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
    @DecimalMax(value = "9999999999999.99", message = "Price exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal pricePerMonth;

    private java.util.UUID typeId;
}

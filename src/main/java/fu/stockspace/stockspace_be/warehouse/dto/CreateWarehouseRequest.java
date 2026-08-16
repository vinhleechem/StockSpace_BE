package fu.stockspace.stockspace_be.warehouse.dto;

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




@Getter
@Setter
public class CreateWarehouseRequest {

    @NotNull(message = "Loại kho không được để trống")
    private java.util.UUID typeId;

    @NotBlank(message = "Tên kho không được để trống")
    @Size(max = 255, message = "Tên kho tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    private String description;

    @NotNull(message = "Sức chứa không được để trống")
    @DecimalMin(value = "1.0", message = "Sức chứa phải lớn hơn 0")
    @DecimalMax(value = "99999999.99", message = "Sức chứa tối đa là 99,999,999.99 m²")
    private BigDecimal capacity;


    @NotNull(message = "Giá thuê không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
    @DecimalMax(value = "9999999999999.99", message = "Price exceeds the supported limit")
    @Digits(integer = 13, fraction = 2, message = "Price must have at most 13 integer digits and 2 decimal places")
    private BigDecimal pricePerMonth;


    private List<String> imageUrls;
}

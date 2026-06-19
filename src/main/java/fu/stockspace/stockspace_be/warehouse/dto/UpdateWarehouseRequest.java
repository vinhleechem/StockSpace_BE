package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO khi Owner cập nhật thông tin Warehouse.
 * Chỉ cho phép sửa các trường không ảnh hưởng tới duyệt.
 */
@Getter
@Setter
public class UpdateWarehouseRequest {

    @Size(max = 255, message = "Tên kho tối đa 255 ký tự")
    private String name;

    private String address;

    private String description;

    @DecimalMin(value = "1.0", message = "Sức chứa phải lớn hơn 0")
    private BigDecimal capacity;

    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
    private BigDecimal pricePerMonth;

    private java.util.UUID typeId;
}

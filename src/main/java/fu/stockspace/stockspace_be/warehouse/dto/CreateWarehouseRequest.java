package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO nhận dữ liệu khi Owner tạo mới Warehouse.
 */
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
    private BigDecimal capacity;

    @NotNull(message = "Giá thuê không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá thuê phải lớn hơn 0")
    private BigDecimal pricePerMonth;

    /** Danh sách URL ảnh (tuỳ chọn khi tạo, có thể upload sau) */
    private List<String> imageUrls;
}

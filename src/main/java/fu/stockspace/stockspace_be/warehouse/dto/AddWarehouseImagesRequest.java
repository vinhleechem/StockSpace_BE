package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO nhận danh sách URL ảnh cần thêm vào Warehouse.
 */
@Getter
@Setter
public class AddWarehouseImagesRequest {

    @NotNull(message = "Danh sách ảnh không được để trống")
    private List<String> imageUrls;
}

package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO khi Admin từ chối duyệt một bài đăng Warehouse.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectWarehouseRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    private String reason;
}

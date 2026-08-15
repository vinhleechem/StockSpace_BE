package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;




@Getter
@Setter
public class CreateWarehouseTypeRequest {

    @NotBlank(message = "Tên loại kho không được để trống")
    @Size(max = 100, message = "Tên tối đa 100 ký tự")
    private String name;

    private String description;
}

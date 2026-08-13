package fu.stockspace.stockspace_be.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkLayoutSaveRequest {
    @NotNull(message = "Chiều rộng lưới layout không được để trống")
    private Integer width;

    @NotNull(message = "Chiều dài lưới layout không được để trống")
    private Integer length;

    @NotNull(message = "Chiều cao lưới layout không được để trống")
    private Integer height;

    @Valid
    private List<RackSaveRequest> racks;

    /** Mảng vị trí ô lưới, ví dụ: ["1:0","2:1","3:1",...]. 1 = đen/khóa, 0 = trắng/dùng được */
    private List<String> positions;
}

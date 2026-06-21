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

    @NotNull(message = "Chiều cao lưới layout không được để trống")
    private Integer height;

    @Valid
    private List<ZoneSaveRequest> zones;
}

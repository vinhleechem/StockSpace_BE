package fu.stockspace.stockspace_be.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSystemConfigRequest {

    @NotBlank(message = "Giá trị cấu hình không được để trống")
    private String configValue;

    private String description;
}

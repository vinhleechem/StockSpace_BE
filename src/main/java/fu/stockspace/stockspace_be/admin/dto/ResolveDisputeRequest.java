package fu.stockspace.stockspace_be.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveDisputeRequest {

    @NotBlank(message = "Ghi chú của Admin không được để trống")
    private String adminNote;

    @NotBlank(message = "Quyết định xử lý tiền cọc không được để trống")
    @Pattern(regexp = "^(REFUND_TO_TENANT|FORFEIT_TO_OWNER|KEEP_IN_SYSTEM)$", 
             message = "depositResolution phải là REFUND_TO_TENANT hoặc FORFEIT_TO_OWNER hoặc KEEP_IN_SYSTEM")
    private String depositResolution;
}

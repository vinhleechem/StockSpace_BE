package fu.stockspace.stockspace_be.contract.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class OwnerCancelRequest {

    @NotBlank(message = "Lý do hủy thương thảo không được để trống")
    private String reason;

    private List<String> evidenceImages;
}

package fu.stockspace.stockspace_be.contract.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class TenantReportFailedRequest {

    @NotBlank(message = "Lý do báo cáo không được để trống")
    private String reason;

    private List<String> evidenceImages;
}

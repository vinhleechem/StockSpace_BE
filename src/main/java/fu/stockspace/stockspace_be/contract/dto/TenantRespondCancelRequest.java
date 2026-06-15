package fu.stockspace.stockspace_be.contract.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TenantRespondCancelRequest {

    @NotNull(message = "Trường agree không được để trống")
    private Boolean agree;
}

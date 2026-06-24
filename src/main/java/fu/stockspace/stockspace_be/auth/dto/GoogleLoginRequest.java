package fu.stockspace.stockspace_be.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    @NotBlank(message = "Authorization code trống")
    private String code;

}

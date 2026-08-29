package fu.stockspace.stockspace_be.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    @NotBlank(message = "Authorization code trống")
    private String code;






    @Pattern(regexp = "ROLE_OWNER|ROLE_TENANT", message = "Role chỉ được là ROLE_OWNER hoặc ROLE_TENANT")
    private String role;

}

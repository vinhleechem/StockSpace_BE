package fu.stockspace.stockspace_be.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class GoogleLoginRequest {

    @NotBlank(message = "Authorization code trống")
    private String code;

    /**
     * Role mong muốn khi tạo tài khoản mới qua Google.
     * Chỉ chấp nhận: ROLE_OWNER hoặc ROLE_TENANT.
     * Nếu không truyền hoặc không hợp lệ → mặc định ROLE_TENANT.
     */
    @Pattern(regexp = "ROLE_OWNER|ROLE_TENANT", message = "Role chỉ được là ROLE_OWNER hoặc ROLE_TENANT")
    private String role;

}

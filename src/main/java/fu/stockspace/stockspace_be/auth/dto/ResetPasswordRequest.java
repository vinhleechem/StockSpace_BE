package fu.stockspace.stockspace_be.auth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;


@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không hợp lệ")
    private String email;

    @NotBlank(message = "Token đặt lại mật khẩu là bắt buộc")
    private String token;

    @NotBlank(message = "Mật khẩu mới là bắt buộc")
    @Size(min = 6, message = "Mật khẩu mới phải có ít nhất 6 ký tự")
    private String newPassword;
}

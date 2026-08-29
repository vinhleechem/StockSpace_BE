package fu.stockspace.stockspace_be.auth.dto;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.common.validation.PhoneValidationPatterns;
import jakarta.validation.constraints.*;
import lombok.Data;







@Data
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name is required")
    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @Pattern(regexp = PhoneValidationPatterns.VIETNAMESE_MOBILE, message = "Invalid Vietnamese mobile number")
    private String phone;





    @NotNull(message = "Role is required")
    private RoleType role;
}

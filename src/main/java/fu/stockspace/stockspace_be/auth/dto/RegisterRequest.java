package fu.stockspace.stockspace_be.auth.dto;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
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

    @Pattern(regexp = "^(\\+84|0)[0-9]{9,10}$", message = "Invalid Vietnamese phone number")
    private String phone;





    @NotNull(message = "Role is required")
    private RoleType role;
}

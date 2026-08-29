package fu.stockspace.stockspace_be.staff.dto;

import fu.stockspace.stockspace_be.common.validation.PhoneValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;





@Getter
@Setter
public class InviteStaffRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    private String fullName;

    @Pattern(regexp = PhoneValidationPatterns.VIETNAMESE_MOBILE, message = "Số điện thoại di động Việt Nam không hợp lệ")
    private String phone;
}

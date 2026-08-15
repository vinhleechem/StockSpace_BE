package fu.stockspace.stockspace_be.admin.dto;

import fu.stockspace.stockspace_be.common.validation.PhoneValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;






@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {

    @Size(max = 150, message = "Họ tên không vượt quá 150 ký tự")
    private String fullName;

    @Pattern(regexp = PhoneValidationPatterns.VIETNAMESE_MOBILE, message = "Số điện thoại di động Việt Nam không hợp lệ")
    private String phone;
}

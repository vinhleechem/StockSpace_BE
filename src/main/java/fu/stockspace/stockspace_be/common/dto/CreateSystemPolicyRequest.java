package fu.stockspace.stockspace_be.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSystemPolicyRequest {

    @NotBlank(message = "Phiên bản chính sách không được để trống")
    @Size(max = 50, message = "Phiên bản chính sách không được vượt quá 50 ký tự")
    private String version;

    @NotBlank(message = "Nội dung chính sách không được để trống")
    private String content;
}

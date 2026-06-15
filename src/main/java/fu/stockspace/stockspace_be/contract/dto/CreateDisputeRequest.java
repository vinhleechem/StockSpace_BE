package fu.stockspace.stockspace_be.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * DTO khi một bên mở Dispute với hợp đồng.
 */
@Getter
@Setter
public class CreateDisputeRequest {

    @NotNull(message = "ID hợp đồng không được để trống")
    private UUID contractId;

    @NotBlank(message = "Lý do tranh chấp không được để trống")
    private String reason;

    /** Danh sách URL ảnh bằng chứng (tuỳ chọn) */
    private List<String> evidenceImages;
}

package fu.stockspace.stockspace_be.wms.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAuditRequest {

    @NotEmpty(message = "Danh sách items không được để trống")
    @Valid
    private List<SubmitAuditItemRequest> items;
}

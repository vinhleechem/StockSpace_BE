package fu.stockspace.stockspace_be.inspection.dto;

import fu.stockspace.stockspace_be.inspection.entity.InspectionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;




@Getter
@Setter
public class SubmitInspectionRequest {





    private Map<String, Object> checklistData;

    private String notes;

    private List<String> images;

    @NotNull(message = "Kết quả kiểm định không được để trống")
    private InspectionStatus status;
}

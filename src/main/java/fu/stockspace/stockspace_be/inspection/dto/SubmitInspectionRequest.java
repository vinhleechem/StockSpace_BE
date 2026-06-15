package fu.stockspace.stockspace_be.inspection.dto;

import fu.stockspace.stockspace_be.inspection.entity.InspectionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * DTO khi Inspector nộp kết quả kiểm định.
 */
@Getter
@Setter
public class SubmitInspectionRequest {

    /**
     * Kết quả từng hạng mục kiểm định.
     * Ví dụ: { "fireExtinguisher": true, "electricalSafety": false, "structuralIntegrity": true }
     */
    private Map<String, Object> checklistData;

    private String notes;

    @NotNull(message = "Kết quả kiểm định không được để trống")
    private InspectionStatus status; // PASSED hoặc FAILED
}

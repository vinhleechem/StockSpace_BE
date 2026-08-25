package fu.stockspace.stockspace_be.contract.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Reason supplied by a tenant when a submitted contract needs changes or is
 * rejected.
 */
@Getter
@Setter
@Schema(description = "Tenant reason for requesting changes or rejecting a submitted contract")
public class TenantContractDecisionRequest {

    @Schema(example = "Please correct the leased area in section 2.", maxLength = 2000)
    @NotBlank(message = "Reason is required")
    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    private String reason;
}

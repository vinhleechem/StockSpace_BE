package fu.stockspace.stockspace_be.contract.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO trả về thông tin DisputeTicket.
 */
@Getter
@Builder
public class DisputeResponse {

    private UUID id;
    private String status;
    private String reason;
    private String evidenceImages;
    private String adminNote;

    // Contract info
    private UUID contractId;

    // Raised by
    private Long raisedById;
    private String raisedByName;

    // Handled by
    private Long handledById;
    private String handledByName;

    private LocalDateTime createdAt;
}

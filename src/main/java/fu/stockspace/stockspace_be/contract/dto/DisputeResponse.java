package fu.stockspace.stockspace_be.contract.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;





import java.util.UUID;

@Getter
@Builder
public class DisputeResponse {

    private UUID id;
    private String status;
    private String reason;
    private String evidenceImages;
    private String adminNote;


    private UUID contractId;


    private UUID raisedById;
    private String raisedByName;


    private UUID handledById;
    private String handledByName;

    private LocalDateTime createdAt;
}

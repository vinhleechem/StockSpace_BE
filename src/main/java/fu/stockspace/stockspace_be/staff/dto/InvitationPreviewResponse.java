package fu.stockspace.stockspace_be.staff.dto;

import lombok.Builder;
import lombok.Getter;







@Getter
@Builder
public class InvitationPreviewResponse {

    private String email;
    private String fullName;
    private String tenantName;
    private String tenantEmail;


    private boolean valid;


    private String message;
}

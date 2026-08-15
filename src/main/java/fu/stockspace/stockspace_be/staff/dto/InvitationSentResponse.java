package fu.stockspace.stockspace_be.staff.dto;

import lombok.Builder;
import lombok.Getter;





@Getter
@Builder
public class InvitationSentResponse {

    private String email;
    private String fullName;


    private String expiresAt;

    private String message;
}

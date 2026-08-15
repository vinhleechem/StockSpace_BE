package fu.stockspace.stockspace_be.staff.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;





@Getter
@Builder
public class StaffMemberResponse {


    private UUID memberId;


    private UUID userId;

    private String email;
    private String fullName;
    private String phone;
    private String avatarUrl;


    private boolean isActive;


    private LocalDateTime joinedAt;
}

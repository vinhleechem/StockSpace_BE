package fu.stockspace.stockspace_be.staff.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response trả về sau khi gửi lời mời thành công.
 * Cho FE biết lời mời đã được gửi và thông tin cơ bản.
 */
@Getter
@Builder
public class InvitationSentResponse {

    private String email;
    private String fullName;

    /** Thời gian hết hạn của lời mời (ISO 8601) */
    private String expiresAt;

    private String message;
}

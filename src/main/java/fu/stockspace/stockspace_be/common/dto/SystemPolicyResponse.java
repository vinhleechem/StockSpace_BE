package fu.stockspace.stockspace_be.common.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class SystemPolicyResponse {
    private Long id;
    private String version;
    private String content;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

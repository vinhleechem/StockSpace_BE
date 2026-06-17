package fu.stockspace.stockspace_be.common.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

import java.util.UUID;

@Getter
@Builder
public class SystemPolicyResponse {
    private UUID id;
    private String version;
    private String content;
    private boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

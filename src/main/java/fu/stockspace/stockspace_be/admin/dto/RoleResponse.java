package fu.stockspace.stockspace_be.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * DTO trả về thông tin chi tiết của vai trò (Role) kèm permissions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private java.util.UUID id;
    private String name;
    private String description;
    private Set<PermissionResponse> permissions;
}

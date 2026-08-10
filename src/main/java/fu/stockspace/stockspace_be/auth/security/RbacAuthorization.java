package fu.stockspace.stockspace_be.auth.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SpEL entry point used by {@code @PreAuthorize("@rbac.hasPermission('...')")}.
 *
 * <p>This deliberately has no implicit ADMIN bypass.  Platform-wide actions
 * are exposed through explicit admin permissions/endpoints; actions on an
 * Owner's or Tenant's data still require the service-level ownership check.</p>
 */
@Component("rbac")
public class RbacAuthorization {

    public boolean hasPermission(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return false;
        }

        return authentication.getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }

    public boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }
}

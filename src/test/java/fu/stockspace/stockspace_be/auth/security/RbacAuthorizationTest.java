package fu.stockspace.stockspace_be.auth.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacAuthorizationTest {

    private final RbacAuthorization rbac = new RbacAuthorization();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void grantsAccessOnlyWhenTheExactPermissionIsPresent() {
        authenticate("owner@example.com", "WAREHOUSE_CREATE", "WAREHOUSE_READ");

        assertTrue(rbac.hasPermission("WAREHOUSE_CREATE"));
        assertFalse(rbac.hasPermission("WAREHOUSE_DELETE"));
    }

    @Test
    void supportsAnyOfSeveralPermissions() {
        authenticate("staff@example.com", "OUTBOUND_CREATE");

        assertTrue(rbac.hasAnyPermission("INBOUND_CREATE", "OUTBOUND_CREATE"));
        assertFalse(rbac.hasAnyPermission("ADMIN_USER_MANAGE", "STAFF_MANAGE"));
    }

    @Test
    void deniesAnonymousAndUnauthenticatedRequests() {
        assertFalse(rbac.hasPermission("WAREHOUSE_READ"));

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertFalse(rbac.hasPermission("WAREHOUSE_READ"));
    }

    @Test
    void adminRoleAloneDoesNotBypassOwnershipOrPermissionPolicy() {
        authenticate("admin@example.com", "ROLE_ADMIN");

        assertFalse(rbac.hasPermission("CONTRACT_OWNER_MANAGE"));
    }

    private void authenticate(String username, String... authorities) {
        List<SimpleGrantedAuthority> grantedAuthorities = java.util.Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "N/A", grantedAuthorities));
    }
}

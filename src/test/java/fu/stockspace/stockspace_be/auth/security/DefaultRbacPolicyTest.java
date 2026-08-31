package fu.stockspace.stockspace_be.auth.security;

import fu.stockspace.stockspace_be.auth.entity.PermissionCode;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultRbacPolicyTest {

    @Test
    void definesACompleteDefaultGrantSetForEverySystemRole() {
        Map<RoleType, Set<PermissionCode>> grants = DefaultRbacPolicy.grants();

        assertEquals(Set.of(RoleType.values()), grants.keySet());
        grants.values().forEach(rolePermissions -> assertFalse(rolePermissions.isEmpty()));
        assertEquals(Set.of(PermissionCode.values()), grants.get(RoleType.ROLE_ADMIN));
    }

    @Test
    void preservesLeastPrivilegeBetweenBusinessRoles() {
        Map<RoleType, Set<PermissionCode>> grants = DefaultRbacPolicy.grants();

        assertTrue(grants.get(RoleType.ROLE_OWNER).contains(PermissionCode.WAREHOUSE_CREATE));
        assertFalse(grants.get(RoleType.ROLE_TENANT).contains(PermissionCode.WAREHOUSE_CREATE));
        assertTrue(grants.values().stream()
                .flatMap(Set::stream)
                .noneMatch(permission -> permission.name().startsWith("RENTAL_REQUEST_")));
        assertTrue(grants.values().stream()
                .flatMap(Set::stream)
                .noneMatch(permission -> permission.name().startsWith("DISPUTE_")
                        || permission.name().equals("CONTRACT_HANDOVER_CONFIRM")));
        assertFalse(grants.get(RoleType.ROLE_STAFF).contains(PermissionCode.STAFF_MANAGE));
        assertTrue(grants.get(RoleType.ROLE_INSPECTOR).contains(PermissionCode.INSPECTION_EXECUTE));
        grants.values().forEach(rolePermissions ->
                assertTrue(rolePermissions.contains(PermissionCode.PROFILE_UPDATE)));
    }
}

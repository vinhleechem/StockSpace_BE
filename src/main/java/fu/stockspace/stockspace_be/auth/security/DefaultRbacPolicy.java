package fu.stockspace.stockspace_be.auth.security;

import fu.stockspace.stockspace_be.auth.entity.PermissionCode;
import fu.stockspace.stockspace_be.auth.entity.RoleType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public final class DefaultRbacPolicy {

    private DefaultRbacPolicy() {
    }

    public static Map<RoleType, Set<PermissionCode>> grants() {
        Map<RoleType, Set<PermissionCode>> grants = new EnumMap<>(RoleType.class);

        Set<PermissionCode> adminPermissions = EnumSet.allOf(PermissionCode.class);
        adminPermissions.remove(PermissionCode.CHAT_USE);
        grants.put(RoleType.ROLE_ADMIN, adminPermissions);
        grants.put(RoleType.ROLE_OWNER, permissions(
                PermissionCode.AUTH_SESSION_MANAGE, PermissionCode.PROFILE_READ, PermissionCode.PROFILE_UPDATE,
                PermissionCode.MEDIA_UPLOAD,
                PermissionCode.NOTIFICATION_READ, PermissionCode.NOTIFICATION_UPDATE,
                PermissionCode.WALLET_READ, PermissionCode.WALLET_TOP_UP, PermissionCode.WALLET_WITHDRAW,
                PermissionCode.WAREHOUSE_READ, PermissionCode.WAREHOUSE_CREATE,
                PermissionCode.WAREHOUSE_UPDATE, PermissionCode.WAREHOUSE_DELETE,
                PermissionCode.WAREHOUSE_LAYOUT_OWNER_MANAGE,
                PermissionCode.CONTRACT_READ, PermissionCode.CONTRACT_OWNER_MANAGE,
                PermissionCode.INSPECTION_READ,
                PermissionCode.INSPECTION_REQUEST, PermissionCode.OWNER_STATS_READ
        ));
        grants.put(RoleType.ROLE_TENANT, permissions(
                PermissionCode.AUTH_SESSION_MANAGE, PermissionCode.PROFILE_READ, PermissionCode.PROFILE_UPDATE,
                PermissionCode.MEDIA_UPLOAD, PermissionCode.CHAT_USE,
                PermissionCode.NOTIFICATION_READ, PermissionCode.NOTIFICATION_UPDATE,
                PermissionCode.WALLET_READ, PermissionCode.WALLET_TOP_UP, PermissionCode.WALLET_WITHDRAW,
                PermissionCode.WAREHOUSE_READ, PermissionCode.WAREHOUSE_LAYOUT_TENANT_MANAGE,
                PermissionCode.CONTRACT_READ, PermissionCode.CONTRACT_TENANT_MANAGE,
                PermissionCode.INVENTORY_READ,
                PermissionCode.INVENTORY_CREATE, PermissionCode.INVENTORY_UPDATE,
                PermissionCode.INVENTORY_DELETE, PermissionCode.INBOUND_CREATE,
                PermissionCode.OUTBOUND_CREATE, PermissionCode.INVENTORY_AUDIT_MANAGE,
                PermissionCode.PRODUCT_MANAGE, PermissionCode.STAFF_MANAGE,
                PermissionCode.PACKAGE_PURCHASE
        ));
        grants.put(RoleType.ROLE_STAFF, permissions(
                PermissionCode.AUTH_SESSION_MANAGE, PermissionCode.PROFILE_READ, PermissionCode.PROFILE_UPDATE,
                PermissionCode.MEDIA_UPLOAD,
                PermissionCode.NOTIFICATION_READ, PermissionCode.NOTIFICATION_UPDATE,
                PermissionCode.WALLET_READ, PermissionCode.WALLET_TOP_UP, PermissionCode.WALLET_WITHDRAW,
                PermissionCode.WAREHOUSE_READ, PermissionCode.INVENTORY_READ,
                PermissionCode.INVENTORY_CREATE, PermissionCode.INVENTORY_UPDATE,
                PermissionCode.INVENTORY_DELETE, PermissionCode.INBOUND_CREATE,
                PermissionCode.OUTBOUND_CREATE, PermissionCode.INVENTORY_AUDIT_MANAGE,
                PermissionCode.PRODUCT_MANAGE, PermissionCode.STAFF_WORK_HISTORY_READ
        ));
        grants.put(RoleType.ROLE_INSPECTOR, permissions(
                PermissionCode.AUTH_SESSION_MANAGE, PermissionCode.PROFILE_READ, PermissionCode.PROFILE_UPDATE,
                PermissionCode.MEDIA_UPLOAD,
                PermissionCode.NOTIFICATION_READ, PermissionCode.NOTIFICATION_UPDATE,
                PermissionCode.WALLET_READ, PermissionCode.WALLET_TOP_UP, PermissionCode.WALLET_WITHDRAW,
                PermissionCode.WAREHOUSE_READ, PermissionCode.WAREHOUSE_REVIEW,
                PermissionCode.INSPECTION_READ, PermissionCode.INSPECTION_EXECUTE
        ));

        return Collections.unmodifiableMap(grants);
    }

    private static Set<PermissionCode> permissions(PermissionCode... permissions) {
        return permissions.length == 0
                ? EnumSet.noneOf(PermissionCode.class)
                : EnumSet.of(permissions[0], permissions);
    }
}

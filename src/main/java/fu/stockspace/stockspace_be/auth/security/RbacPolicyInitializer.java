package fu.stockspace.stockspace_be.auth.security;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.entity.PermissionCode;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.repository.PermissionRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class RbacPolicyInitializer implements CommandLineRunner {

    private static final String POLICY_VERSION = "2026-09-04.2";
    private static final String POLICY_VERSION_KEY = "rbac_policy_version";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Map<PermissionCode, Permission> permissions = seedPermissions();
        boolean applyDefaultGrants = policyNeedsMigration();

        for (Map.Entry<RoleType, Set<PermissionCode>> entry : DefaultRbacPolicy.grants().entrySet()) {
            Role role = roleRepository.findByName(entry.getKey().name()).orElse(null);
            boolean newRole = role == null;
            if (newRole) {
                role = Role.builder()
                        .name(entry.getKey().name())
                        .description(descriptionFor(entry.getKey()))
                        .permissions(new HashSet<>())
                        .build();
            }

            if (newRole || applyDefaultGrants) {
                Set<Permission> grants = new HashSet<>();
                entry.getValue().forEach(code -> grants.add(permissions.get(code)));
                role.setPermissions(grants);
            }
            roleRepository.save(role);
        }

        if (applyDefaultGrants) {
            SystemConfig marker = systemConfigRepository.findByConfigKey(POLICY_VERSION_KEY)
                    .orElse(SystemConfig.builder().configKey(POLICY_VERSION_KEY).build());
            marker.setConfigValue(POLICY_VERSION);
            marker.setDescription("Internal version marker for default RBAC grants");
            systemConfigRepository.save(marker);
            log.info("Applied RBAC policy version {}", POLICY_VERSION);
        }
    }

    private Map<PermissionCode, Permission> seedPermissions() {
        Map<PermissionCode, Permission> permissions = new EnumMap<>(PermissionCode.class);
        for (PermissionCode code : PermissionCode.values()) {
            Permission permission = permissionRepository.findByName(code.name())
                    .orElseGet(() -> permissionRepository.save(Permission.builder()
                            .name(code.name())
                            .description(code.getDescription())
                            .build()));
            permissions.put(code, permission);
        }
        return permissions;
    }

    private boolean policyNeedsMigration() {
        return systemConfigRepository.findByConfigKey(POLICY_VERSION_KEY)
                .map(marker -> !POLICY_VERSION.equals(marker.getConfigValue()))
                .orElse(true);
    }

    private String descriptionFor(RoleType roleType) {
        return switch (roleType) {
            case ROLE_ADMIN -> "Platform administrator";
            case ROLE_OWNER -> "Warehouse owner";
            case ROLE_TENANT -> "Warehouse tenant";
            case ROLE_STAFF -> "Tenant warehouse staff";
            case ROLE_INSPECTOR -> "Warehouse inspector";
        };
    }
}

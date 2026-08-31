package fu.stockspace.stockspace_be.auth.security;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.entity.PermissionCode;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.repository.PermissionRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RbacPolicyInitializerTest {

    @Test
    void seedsEveryCanonicalPermissionAndAllDefaultRoles() {
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);
        when(permissionRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(systemConfigRepository.findByConfigKey(anyString())).thenReturn(Optional.empty());
        when(systemConfigRepository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        new RbacPolicyInitializer(permissionRepository, roleRepository, systemConfigRepository).run();

        ArgumentCaptor<Permission> permissionCaptor = ArgumentCaptor.forClass(Permission.class);
        org.mockito.Mockito.verify(permissionRepository, org.mockito.Mockito.times(PermissionCode.values().length))
                .save(permissionCaptor.capture());
        assertEquals(PermissionCode.values().length, permissionCaptor.getAllValues().size());

        ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
        org.mockito.Mockito.verify(roleRepository, org.mockito.Mockito.times(RoleType.values().length))
                .save(roleCaptor.capture());
        Role owner = roleCaptor.getAllValues().stream()
                .filter(role -> RoleType.ROLE_OWNER.name().equals(role.getName()))
                .findFirst()
                .orElseThrow();
        assertTrue(owner.getPermissions().stream()
                .anyMatch(permission -> PermissionCode.WAREHOUSE_CREATE.name().equals(permission.getName())));
    }

    @Test
    void doesNotOverwriteAnExistingRoleAfterThePolicyVersionWasApplied() {
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        SystemConfigRepository systemConfigRepository = mock(SystemConfigRepository.class);
        Permission customPermission = Permission.builder().name("CUSTOM_REPORT_READ").build();
        Role existingOwner = Role.builder()
                .name(RoleType.ROLE_OWNER.name())
                .permissions(new HashSet<>(List.of(customPermission)))
                .build();

        when(permissionRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByName(anyString())).thenAnswer(invocation ->
                RoleType.ROLE_OWNER.name().equals(invocation.getArgument(0))
                        ? Optional.of(existingOwner)
                        : Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(systemConfigRepository.findByConfigKey("rbac_policy_version")).thenReturn(Optional.of(
                SystemConfig.builder().configKey("rbac_policy_version").configValue("2026-08-31.1").build()));

        new RbacPolicyInitializer(permissionRepository, roleRepository, systemConfigRepository).run();

        assertEquals(List.of("CUSTOM_REPORT_READ"), new ArrayList<>(existingOwner.getPermissions()).stream()
                .map(Permission::getName)
                .toList());
    }
}

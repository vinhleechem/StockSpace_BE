package fu.stockspace.stockspace_be.admin.service;

import fu.stockspace.stockspace_be.admin.dto.AssignPermissionRequest;
import fu.stockspace.stockspace_be.admin.dto.AssignRoleRequest;
import fu.stockspace.stockspace_be.admin.dto.CreateRoleRequest;
import fu.stockspace.stockspace_be.admin.dto.PermissionResponse;
import fu.stockspace.stockspace_be.admin.dto.RoleResponse;
import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.PermissionRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service xử lý các nghiệp vụ liên quan đến Role và quản lý vai trò của User cho Admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    private static final Set<String> SYSTEM_ROLES = Set.of(
            RoleType.ROLE_ADMIN.name(),
            RoleType.ROLE_OWNER.name(),
            RoleType.ROLE_TENANT.name(),
            RoleType.ROLE_STAFF.name(),
            RoleType.ROLE_INSPECTOR.name()
    );

    /**
     * Lấy danh sách tất cả các vai trò trong hệ thống.
     */
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        log.info("Fetching all roles from database");
        return roleRepository.findAll().stream()
                .map(this::mapToRoleResponse)
                .collect(Collectors.toList());
    }

    /**
     * Tạo vai trò mới.
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        String name = request.getName().trim().toUpperCase().replace(" ", "_");
        if (!name.startsWith("ROLE_")) {
            name = "ROLE_" + name;
        }

        log.info("Creating new role with name: {}", name);

        if (roleRepository.findByName(name).isPresent()) {
            log.warn("Role name already exists: {}", name);
            throw new ResourceConflictException(ErrorCode.ROLE_ALREADY_EXISTS);
        }

        Role role = Role.builder()
                .name(name)
                .description(request.getDescription())
                .isActive(true)
                .build();

        role = roleRepository.save(role);
        log.info("Role created successfully with ID: {}", role.getId());

        return mapToRoleResponse(role);
    }

    /**
     * Cập nhật thông tin vai trò.
     */
    @Transactional
    public RoleResponse updateRole(Long id, CreateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        String newName = request.getName().trim().toUpperCase().replace(" ", "_");
        if (!newName.startsWith("ROLE_")) {
            newName = "ROLE_" + newName;
        }

        log.info("Updating role ID: {} from name '{}' to '{}'", id, role.getName(), newName);

        // Bảo vệ vai trò hệ thống không bị đổi tên
        if (SYSTEM_ROLES.contains(role.getName()) && !role.getName().equalsIgnoreCase(newName)) {
            log.warn("Attempt to rename system role: {}", role.getName());
            throw new BadRequestException("Không thể đổi tên các vai trò mặc định của hệ thống");
        }

        // Kiểm tra trùng tên với role khác
        if (!role.getName().equalsIgnoreCase(newName)) {
            if (roleRepository.findByName(newName).isPresent()) {
                log.warn("Target role name already exists: {}", newName);
                throw new ResourceConflictException(ErrorCode.ROLE_ALREADY_EXISTS);
            }
        }

        role.setName(newName);
        role.setDescription(request.getDescription());

        role = roleRepository.save(role);
        log.info("Role ID: {} updated successfully", id);

        return mapToRoleResponse(role);
    }

    /**
     * Xóa vai trò.
     */
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        log.info("Deleting role: {}", role.getName());

        // Bảo vệ vai trò hệ thống không bị xóa
        if (SYSTEM_ROLES.contains(role.getName())) {
            log.warn("Attempt to delete system role: {}", role.getName());
            throw new BadRequestException("Không thể xóa các vai trò mặc định của hệ thống");
        }

        // Gỡ bỏ role khỏi toàn bộ User trước khi xóa role (tránh Foreign Key Constraint lỗi trên user_roles)
        List<User> users = userRepository.findUsersByRoleId(id);
        if (!users.isEmpty()) {
            log.info("Removing role {} from {} users", role.getName(), users.size());
            for (User user : users) {
                user.getRoles().remove(role);
                userRepository.save(user);
            }
        }

        role.setDeleted(true);
        roleRepository.save(role);
        log.info("Role ID: {} deleted successfully", id);
    }

    /**
     * Gán thêm một Permission vào Role.
     */
    @Transactional
    public RoleResponse assignPermissionToRole(Long roleId, AssignPermissionRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        Permission permission = permissionRepository.findById(request.getPermissionId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PERMISSION_NOT_FOUND));

        log.info("Assigning permission '{}' to role '{}'", permission.getName(), role.getName());

        role.getPermissions().add(permission);
        role = roleRepository.save(role);

        return mapToRoleResponse(role);
    }

    /**
     * Gỡ bỏ Permission khỏi Role.
     */
    @Transactional
    public RoleResponse removePermissionFromRole(Long roleId, Long permissionId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PERMISSION_NOT_FOUND));

        log.info("Removing permission '{}' from role '{}'", permission.getName(), role.getName());

        if (!role.getPermissions().contains(permission)) {
            log.warn("Permission '{}' is not assigned to role '{}'", permission.getName(), role.getName());
            throw new BadRequestException("Quyền hạn này chưa được gán cho vai trò");
        }

        role.getPermissions().remove(permission);
        role = roleRepository.save(role);

        return mapToRoleResponse(role);
    }

    /**
     * Gán vai trò cho User (thêm vào user_roles).
     */
    @Transactional
    public void assignRoleToUser(java.util.UUID userId, AssignRoleRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        log.info("Assigning role '{}' to user '{}'", role.getName(), user.getEmail());

        user.getRoles().add(role);
        userRepository.save(user);
    }

    /**
     * Xóa vai trò khỏi User.
     */
    @Transactional
    public void removeRoleFromUser(java.util.UUID userId, Long roleId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        log.info("Removing role '{}' from user '{}'", role.getName(), user.getEmail());

        if (!user.getRoles().contains(role)) {
            log.warn("Role '{}' is not assigned to user '{}'", role.getName(), user.getEmail());
            throw new BadRequestException("Người dùng chưa có vai trò này");
        }

        // Ràng buộc nghiệp vụ: Một user phải có ít nhất 1 role để tránh dữ liệu mồ côi
        if (user.getRoles().size() <= 1) {
            log.warn("Attempt to remove last role from user: {}", user.getEmail());
            throw new BadRequestException("Người dùng phải có ít nhất một vai trò hoạt động");
        }

        user.getRoles().remove(role);
        userRepository.save(user);
    }

    private RoleResponse mapToRoleResponse(Role role) {
        Set<PermissionResponse> permissions = role.getPermissions().stream()
                .map(permission -> PermissionResponse.builder()
                        .id(permission.getId())
                        .name(permission.getName())
                        .description(permission.getDescription())
                        .build())
                .collect(Collectors.toSet());

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissions)
                .build();
    }
}

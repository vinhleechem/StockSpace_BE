package fu.stockspace.stockspace_be.admin.service;

import fu.stockspace.stockspace_be.admin.dto.CreatePermissionRequest;
import fu.stockspace.stockspace_be.admin.dto.PermissionResponse;
import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.repository.PermissionRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service xử lý các nghiệp vụ liên quan đến Permission cho Admin.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionManagementService {

    private final PermissionRepository permissionRepository;

    /**
     * Lấy danh sách tất cả các quyền (Permissions) trong hệ thống.
     */
    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        log.info("Fetching all permissions from database");
        return permissionRepository.findAll().stream()
                .map(this::mapToPermissionResponse)
                .collect(Collectors.toList());
    }

    /**
     * Tạo mới một Permission trong hệ thống.
     */
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        String name = request.getName().trim().toUpperCase().replace(" ", "_");

        log.info("Creating new permission with name: {}", name);

        if (permissionRepository.findByName(name).isPresent()) {
            log.warn("Permission name already exists: {}", name);
            throw new ResourceConflictException(ErrorCode.PERMISSION_ALREADY_EXISTS);
        }

        Permission permission = Permission.builder()
                .name(name)
                .description(request.getDescription())
                .isActive(true)
                .build();

        permission = permissionRepository.save(permission);
        log.info("Permission created successfully with ID: {}", permission.getId());

        return mapToPermissionResponse(permission);
    }

    private PermissionResponse mapToPermissionResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .description(permission.getDescription())
                .build();
    }
}

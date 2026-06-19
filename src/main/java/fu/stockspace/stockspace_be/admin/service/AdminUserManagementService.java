package fu.stockspace.stockspace_be.admin.service;

import fu.stockspace.stockspace_be.admin.dto.*;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service xử lý nghiệp vụ quản lý người dùng (User Management) cho Admin.
 *
 * Chức năng:
 * - Xem danh sách / tìm kiếm user (phân trang, filter theo role/status)
 * - Xem chi tiết user
 * - Tạo user mới (Admin có thể tạo bất kỳ role)
 * - Cập nhật thông tin user
 * - Kích hoạt / khóa tài khoản
 * - Đặt lại mật khẩu
 * - Xóa user
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // ==================== Query ====================

    /**
     * Lấy danh sách người dùng có phân trang, tìm kiếm và lọc.
     *
     * @param keyword  Từ khóa tìm kiếm (email / fullName / phone), null = tất cả
     * @param roleName Lọc theo tên role (ví dụ: ROLE_OWNER), null = tất cả
     * @param isActive Lọc theo trạng thái, null = tất cả
     * @param page     Số trang (0-indexed)
     * @param size     Số phần tử mỗi trang
     * @param sortBy   Trường sắp xếp (createdAt, fullName, email)
     * @param sortDir  Chiều sắp xếp (asc/desc)
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsers(String keyword, String roleName, Boolean isActive,
                                      int page, int size, String sortBy, String sortDir) {

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        // Normalize keyword: blank → null để JPQL IS NULL check pass
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<User> userPage;
        if (StringUtils.hasText(roleName) && isActive != null) {
            // Filter cả role lẫn status: dùng kết hợp trong memory (2 query nhỏ)
            userPage = userRepository.searchUsersByRole(kw, roleName.trim(), pageable);
            List<User> filtered = userPage.getContent().stream()
                    .filter(u -> u.isActive() == isActive)
                    .collect(Collectors.toList());
            // Wrap lại bằng custom paged (chấp nhận tổng trang bị lệch khi kết hợp 2 filter)
            return buildPagedResponse(filtered, page, size,
                    filtered.size(), userPage.getTotalPages());
        } else if (StringUtils.hasText(roleName)) {
            userPage = userRepository.searchUsersByRole(kw, roleName.trim(), pageable);
        } else if (isActive != null) {
            userPage = userRepository.searchUsersByStatus(kw, isActive, pageable);
        } else {
            userPage = userRepository.searchUsers(kw, pageable);
        }

        List<UserResponse> content = userPage.getContent().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());

        return PagedResponse.<UserResponse>builder()
                .content(content)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    /**
     * Xem chi tiết thông tin một User theo ID.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        log.info("Admin fetched user detail for ID: {}", id);
        return mapToUserResponse(user);
    }

    // ==================== Create ====================

    /**
     * Admin tạo mới một User với role bất kỳ.
     * Khác với self-register (chỉ OWNER/TENANT), Admin tạo được cả ADMIN, STAFF, INSPECTOR, v.v.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Admin creating new user with email: {}", request.getEmail());

        // Kiểm tra email trùng
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new ResourceConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // Load các role được chỉ định
        Set<Role> roles = loadRoles(request.getRoleIds());

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .roles(roles)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("User created successfully by admin: {} (ID: {})", user.getEmail(), user.getId());

        return mapToUserResponse(user);
    }

    // ==================== Update ====================

    /**
     * Cập nhật thông tin cơ bản của User (fullName, phone).
     * Email không được thay đổi — dùng làm định danh duy nhất.
     * Để thay đổi role → dùng API assign/remove role trong AdminRoleController.
     */
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        log.info("Admin updating user ID: {}", id);

        if (StringUtils.hasText(request.getFullName())) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }

        user = userRepository.save(user);
        log.info("User ID: {} updated successfully", id);

        return mapToUserResponse(user);
    }

    // ==================== Status Toggle ====================

    /**
     * Kích hoạt hoặc khóa tài khoản User.
     *
     * Ràng buộc:
     * - Admin không thể tự khóa tài khoản của chính mình
     * - Admin không thể khóa tài khoản Admin khác (bảo vệ hệ thống)
     *
     * @param id       ID của user cần thay đổi
     * @param activate true = mở khóa, false = khóa
     */
    @Transactional
    public UserResponse setUserStatus(UUID id, boolean activate) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // Lấy email của Admin đang thực hiện thao tác
        String currentAdminEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        // Không tự khóa chính mình
        if (!activate && user.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DEACTIVATE_SELF);
        }

        // Không khóa Admin khác (chỉ cần check khi deactivate)
        if (!activate && hasAdminRole(user)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DEACTIVATE_SELF);
        }

        String action = activate ? "activated" : "deactivated";
        log.info("Admin {} {} user ID: {}", currentAdminEmail, action, id);

        user.setActive(activate);
        user = userRepository.save(user);

        return mapToUserResponse(user);
    }

    // ==================== Password Reset ====================

    /**
     * Admin đặt lại mật khẩu cho User.
     * Không cần biết mật khẩu cũ — quyền Admin.
     */
    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // Kiểm tra confirmPassword khớp
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException(ErrorCode.PASSWORD_MISMATCH);
        }

        log.info("Admin resetting password for user ID: {}", id);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password reset successfully for user ID: {}", id);
    }

    // ==================== Delete ====================

    /**
     * Xóa vĩnh viễn User khỏi hệ thống.
     *
     * Ràng buộc:
     * - Không thể xóa tài khoản có role ADMIN
     * - Không thể xóa chính mình
     */
    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        String currentAdminEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        // Không tự xóa chính mình
        if (user.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DELETE_ADMIN);
        }

        // Không xóa tài khoản Admin khác
        if (hasAdminRole(user)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DELETE_ADMIN);
        }

        log.info("Admin {} deleting user ID: {} ({})", currentAdminEmail, id, user.getEmail());
        user.setDeleted(true);
        userRepository.save(user);
        log.info("User ID: {} deleted successfully", id);
    }

    // ==================== Private helpers ====================

    /**
     * Load và validate danh sách Role từ set roleId.
     */
    private Set<Role> loadRoles(Set<java.util.UUID> roleIds) {
        Set<Role> roles = new HashSet<>();
        for (java.util.UUID roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));
            roles.add(role);
        }
        return roles;
    }

    /**
     * Kiểm tra user có role ADMIN không.
     */
    private boolean hasAdminRole(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> RoleType.ROLE_ADMIN.name().equals(r.getName()));
    }

    /**
     * Chuyển User entity sang UserResponse DTO.
     */
    private UserResponse mapToUserResponse(User user) {
        Set<RoleResponse> roleResponses = user.getRoles().stream()
                .map(role -> RoleResponse.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .description(role.getDescription())
                        .build())
                .collect(Collectors.toSet());

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .roles(roleResponses)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    /**
     * Build PagedResponse từ danh sách đã filter sẵn (dùng khi kết hợp 2 filter).
     */
    private PagedResponse<UserResponse> buildPagedResponse(List<User> users, int page, int size,
                                                  long total, int totalPages) {
        List<UserResponse> content = users.stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
        return PagedResponse.<UserResponse>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .last(page >= totalPages - 1)
                .build();
    }
}

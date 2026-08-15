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













@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;














    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> getUsers(String keyword, String roleName, Boolean isActive,
                                      int page, int size, String sortBy, String sortDir) {

        Sort sort = "asc".equalsIgnoreCase(sortDir)
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);


        String kw = StringUtils.hasText(keyword) ? keyword.trim() : "";

        Page<User> userPage;
        if (StringUtils.hasText(roleName) && isActive != null) {

            userPage = userRepository.searchUsersByRole(kw, roleName.trim(), pageable);
            List<User> filtered = userPage.getContent().stream()
                    .filter(u -> u.isActive() == isActive)
                    .collect(Collectors.toList());

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




    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        log.info("Admin fetched user detail for ID: {}", id);
        return mapToUserResponse(user);
    }







    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        log.info("Admin creating new user with email: {}", request.getEmail());


        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new ResourceConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }


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













    @Transactional
    public UserResponse setUserStatus(UUID id, boolean activate) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));


        String currentAdminEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();


        if (!activate && user.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DEACTIVATE_SELF);
        }


        if (!activate && hasAdminRole(user)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DEACTIVATE_SELF);
        }

        String action = activate ? "activated" : "deactivated";
        log.info("Admin {} {} user ID: {}", currentAdminEmail, action, id);

        user.setActive(activate);
        user = userRepository.save(user);

        return mapToUserResponse(user);
    }







    @Transactional
    public void resetPassword(UUID id, ResetPasswordRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));


        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException(ErrorCode.PASSWORD_MISMATCH);
        }

        log.info("Admin resetting password for user ID: {}", id);

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password reset successfully for user ID: {}", id);
    }










    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        String currentAdminEmail = SecurityContextHolder.getContext()
                .getAuthentication().getName();


        if (user.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DELETE_ADMIN);
        }


        if (hasAdminRole(user)) {
            throw new ForbiddenException(ErrorCode.CANNOT_DELETE_ADMIN);
        }

        log.info("Admin {} deleting user ID: {} ({})", currentAdminEmail, id, user.getEmail());
        user.setDeleted(true);
        userRepository.save(user);
        log.info("User ID: {} deleted successfully", id);
    }






    private Set<Role> loadRoles(Set<java.util.UUID> roleIds) {
        Set<Role> roles = new HashSet<>();
        for (java.util.UUID roleId : roleIds) {
            Role role = roleRepository.findById(roleId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));
            roles.add(role);
        }
        return roles;
    }




    private boolean hasAdminRole(User user) {
        return user.getRoles().stream()
                .anyMatch(r -> RoleType.ROLE_ADMIN.name().equals(r.getName()));
    }




    private UserResponse mapToUserResponse(User user) {
        Set<RoleResponse> roleResponses = user.getRoles().stream()
                .map(role -> RoleResponse.builder()
                        .id(role.getId())
                        .name(role.getName())
                        .description(role.getDescription())
                        .permissions(role.getPermissions() == null ? null : role.getPermissions().stream()
                                .map(p -> PermissionResponse.builder()
                                        .id(p.getId())
                                        .name(p.getName())
                                        .description(p.getDescription())
                                        .build())
                                .collect(Collectors.toSet()))
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

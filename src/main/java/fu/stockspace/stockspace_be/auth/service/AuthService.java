package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.dto.LoginRequest;
import fu.stockspace.stockspace_be.auth.dto.LoginResponse;
import fu.stockspace.stockspace_be.auth.dto.RegisterRequest;
import fu.stockspace.stockspace_be.auth.entity.RefreshToken;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service xử lý logic đăng ký, đăng nhập, refresh token, logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    // Chỉ OWNER và TENANT được tự register
    private static final Set<RoleType> SELF_REGISTER_ROLES = Set.of(RoleType.ROLE_OWNER, RoleType.ROLE_TENANT);

    // ==================== Register ====================

    /**
     * Đăng ký tài khoản mới (chỉ OWNER và TENANT).
     * Trả về accessToken trong body + refreshToken qua cookie (set bởi controller).
     */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (!SELF_REGISTER_ROLES.contains(request.getRole())) {
            throw new IllegalArgumentException(
                    "Self-registration is only allowed for OWNER and TENANT roles"
            );
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already registered: " + request.getEmail()
            );
        }

        Role dbRole = roleRepository.findByName(request.getRole().name())
                .orElseThrow(() -> new IllegalArgumentException("Role not found in database: " + request.getRole()));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .roles(Set.of(dbRole))
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} with role {}", user.getEmail(), dbRole.getName());

        return buildAuthResult(user);
    }

    // ==================== Login ====================

    /**
     * Đăng nhập — trả về accessToken trong body + refreshToken qua cookie.
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found after authentication"));

        String userRolesStr = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.joining(","));
        log.info("User logged in: {} with roles [{}]", user.getEmail(), userRolesStr);
        return buildAuthResult(user);
    }

    // ==================== Refresh Token ====================

    /**
     * Dùng refresh token (từ cookie) để lấy access token mới.
     * Áp dụng Refresh Token Rotation: xóa token cũ, tạo token mới.
     */
    @Transactional
    public AuthResult refresh(String refreshTokenValue) {
        // Validate token trong DB
        RefreshToken oldRefreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);
        User user = oldRefreshToken.getUser();

        // Refresh Token Rotation — xóa token cũ, tạo token mới
        // → Nếu token cũ bị đánh cắp và dùng lại → sẽ bị từ chối
        refreshTokenService.deleteToken(refreshTokenValue);

        log.info("Token refreshed for user: {}", user.getEmail());
        return buildAuthResult(user);
    }

    // ==================== Logout ====================

    /**
     * Logout khỏi thiết bị hiện tại — xóa refresh token này.
     */
    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenService.deleteToken(refreshTokenValue);
        }
    }

    /**
     * Logout khỏi tất cả thiết bị — xóa hết refresh token của user.
     */
    @Transactional
    public void logoutAll(User user) {
        refreshTokenService.deleteAllTokensForUser(user);
    }

    // ==================== Private helpers ====================

    /**
     * Tạo access token + refresh token cho user.
     * Đóng gói vào AuthResult để controller dùng.
     */
    private AuthResult buildAuthResult(User user) {
        String accessToken = jwtUtil.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        String primaryRole = user.getRoles().stream()
                .map(Role::getName)
                .findFirst()
                .orElse("");

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(primaryRole)
                .build();

        return new AuthResult(loginResponse, refreshToken.getToken());
    }

    /**
     * Wrapper chứa LoginResponse (cho JSON body) + refreshTokenValue (cho cookie).
     */
    public record AuthResult(LoginResponse loginResponse, String refreshTokenValue) {}
}

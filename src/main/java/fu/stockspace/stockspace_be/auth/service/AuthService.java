package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.dto.*;
import fu.stockspace.stockspace_be.auth.entity.*;
import fu.stockspace.stockspace_be.auth.httpclient.OutboundAuthClient;
import fu.stockspace.stockspace_be.auth.httpclient.OutboundUserClient;
import fu.stockspace.stockspace_be.auth.repository.PasswordResetTokenRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.security.JwtUtil;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service xử lý logic đăng ký, đăng nhập, refresh token, logout,
 * quên mật khẩu và đăng nhập Google OAuth.
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
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OutboundAuthClient outboundAuthClient;
    private final OutboundUserClient outboundUserClient;

    @Value("${app.google.client-id}")
    private String googleClientId;

    @Value("${app.google.client-secret}")
    private String googleClientSecret;

    @Value("${app.google.redirect-uri}")
    private String googleRedirectUri;

    // Chỉ OWNER và TENANT được tự register
    private static final Set<RoleType> SELF_REGISTER_ROLES = Set.of(RoleType.ROLE_OWNER, RoleType.ROLE_TENANT);

    private static final String GOOGLE_GRANT_TYPE = "authorization_code";
    private static final String GOOGLE_RESPONSE_FORMAT = "json";
    private static final int OTP_EXPIRY_MINUTES = 15;

    private final SecureRandom secureRandom = new SecureRandom();

    // ==================== Register ====================

    /**
     * Đăng ký tài khoản mới (chỉ OWNER và TENANT).
     * Gửi email chào mừng bất đồng bộ sau khi tạo thành công.
     */
    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (!SELF_REGISTER_ROLES.contains(request.getRole())) {
            throw new BadRequestException(ErrorCode.ROLE_NOT_SUPPORTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }

        Role dbRole = roleRepository.findByName(request.getRole().name())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .roles(Set.of(dbRole))
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} with role {}", user.getEmail(), dbRole.getName());

        // Gửi email chào mừng (bất đồng bộ — không block response)
        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());

        return buildAuthResult(user);
    }

    // ==================== Login ====================

    /**
     * Đăng nhập — trả về accessToken trong body + refreshToken qua cookie.
     */
    @Transactional
    public AuthResult login(LoginRequest request) {
        // Kiểm tra xem user có dùng Google account không
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.GOOGLE) {
                throw new ResourceConflictException(ErrorCode.CANNOT_LOGIN_GOOGLE_WITH_PASSWORD);
            }
        });

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        String userRolesStr = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.joining(","));
        log.info("User logged in: {} with roles [{}]", user.getEmail(), userRolesStr);
        return buildAuthResult(user);
    }

    // ==================== Google OAuth ====================

    /**
     * Đăng nhập / Đăng ký bằng Google OAuth authorization code.
     * Flow:
     *  1. Exchange code → Google access token (OutboundAuthClient)
     *  2. Dùng access token → lấy Google user info (OutboundUserClient)
     *  3. Tìm user theo email:
     *     - Đã tồn tại (LOCAL): ném lỗi → "dùng email/password"
     *     - Đã tồn tại (GOOGLE): login bình thường
     *     - Chưa có: tạo mới với role TENANT
     */
    @Transactional
    public AuthResult loginWithGoogle(String code) {
        try {
            // Bước 1: Exchange code → access token
            ExchangeTokenRequest tokenRequest = ExchangeTokenRequest.builder()
                    .code(code)
                    .clientId(googleClientId)
                    .clientSecret(googleClientSecret)
                    .redirectUri(googleRedirectUri)
                    .grantType(GOOGLE_GRANT_TYPE)
                    .build();

            ExchangeTokenResponse tokenResponse = outboundAuthClient.exchangeToken(tokenRequest);

            // Bước 2: Lấy thông tin user từ Google
            GoogleUserInfo userInfo = outboundUserClient.getUserInfo(GOOGLE_RESPONSE_FORMAT, tokenResponse.getAccessToken());
            log.info("Google OAuth: user info retrieved for email: {}", userInfo.getEmail());

            // Bước 3: Xử lý user
            User user = userRepository.findByEmail(userInfo.getEmail())
                    .map(existing -> {
                        if (!existing.isActive()) {
                            throw new ResourceConflictException(ErrorCode.USER_LOCKED);
                        }
                        // Nếu là tài khoản LOCAL → không cho login bằng Google
                        if (existing.getProvider() == AuthProvider.LOCAL) {
                            throw new ResourceConflictException(ErrorCode.CANNOT_LOGIN_PASSWORD_WITH_GOOGLE);
                        }
                        // Tài khoản GOOGLE → cập nhật avatar (có thể thay đổi)
                        existing.setAvatarUrl(userInfo.getPicture());
                        return userRepository.save(existing);
                    })
                    .orElseGet(() -> {
                        // Tạo user mới với role ROLE_TENANT
                        Role tenantRole = roleRepository.findByName(RoleType.ROLE_TENANT.name())
                                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

                        // Build fullName từ givenName + familyName hoặc dùng name
                        String fullName = userInfo.getName() != null ? userInfo.getName()
                                : ((userInfo.getGivenName() != null ? userInfo.getGivenName() : "")
                                + " " + (userInfo.getFamilyName() != null ? userInfo.getFamilyName() : "")).trim();
                        if (fullName.isBlank()) fullName = userInfo.getEmail();

                        User newUser = User.builder()
                                .email(userInfo.getEmail())
                                .password(passwordEncoder.encode(generateRandomPassword()))
                                .fullName(fullName)
                                .avatarUrl(userInfo.getPicture())
                                .provider(AuthProvider.GOOGLE)
                                .roles(Set.of(tenantRole))
                                .isActive(true)
                                .build();

                        User saved = userRepository.save(newUser);
                        log.info("New Google user registered: {}", saved.getEmail());
                        // Gửi email chào mừng
                        emailService.sendWelcomeEmail(saved.getEmail(), saved.getFullName());
                        return saved;
                    });

            return buildAuthResult(user);

        } catch (ResourceConflictException | ResourceNotFoundException e) {
            throw e; // Re-throw business exceptions
        } catch (Exception e) {
            log.error("Google OAuth login failed: {}", e.getMessage(), e);
            throw new BadRequestException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }

    // ==================== Forgot Password ====================

    /**
     * Gửi đường dẫn đặt lại mật khẩu về email.
     * Luôn trả về thành công dù email không tồn tại (bảo mật — tránh leak email).
     */
    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.GOOGLE) {
                // Google account không có mật khẩu để reset
                log.warn("Forgot password attempted for Google account: {}", email);
                return;
            }

            // Xóa token cũ (nếu có)
            passwordResetTokenRepository.deleteAllByUser(user);

            // Sinh UUID token
            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);

            // Gửi email đặt lại mật khẩu (bất đồng bộ)
            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
            log.info("Password reset token sent for user: {}", email);
        });
    }

    /**
     * Xác thực token và đặt lại mật khẩu mới.
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new BadRequestException(ErrorCode.INVALID_RESET_TOKEN);
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByUserAndToken(user, request.getToken())
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_RESET_TOKEN));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new BadRequestException(ErrorCode.RESET_TOKEN_EXPIRED);
        }

        // Đặt lại mật khẩu
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Xóa token đã dùng
        passwordResetTokenRepository.delete(resetToken);

        // Logout tất cả thiết bị (vì mật khẩu đã đổi)
        refreshTokenService.deleteAllTokensForUser(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }

    // ==================== Refresh Token ====================

    /**
     * Dùng refresh token (từ cookie) để lấy access token mới.
     */
    @Transactional
    public AuthResult refresh(String refreshTokenValue) {
        RefreshToken oldRefreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);
        User user = oldRefreshToken.getUser();

        refreshTokenService.deleteToken(refreshTokenValue);

        log.info("Token refreshed for user: {}", user.getEmail());
        return buildAuthResult(user);
    }

    // ==================== Logout ====================

    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenService.deleteToken(refreshTokenValue);
        }
    }

    @Transactional
    public void logoutAll(User user) {
        refreshTokenService.deleteAllTokensForUser(user);
    }

    // ==================== Private helpers ====================

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

    /** Sinh mật khẩu ngẫu nhiên cho tài khoản Google (không dùng để login) */
    private String generateRandomPassword() {
        return UUID.randomUUID().toString();
    }

    public record AuthResult(LoginResponse loginResponse, String refreshTokenValue) {}
}

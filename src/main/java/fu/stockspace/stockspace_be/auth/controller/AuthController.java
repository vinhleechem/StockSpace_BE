package fu.stockspace.stockspace_be.auth.controller;

import fu.stockspace.stockspace_be.auth.dto.LoginRequest;
import fu.stockspace.stockspace_be.auth.dto.LoginResponse;
import fu.stockspace.stockspace_be.auth.dto.RegisterRequest;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.service.AuthService;
import fu.stockspace.stockspace_be.auth.service.RefreshTokenService;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller xử lý authentication endpoints.
 *
 * Endpoints:
 *   POST /api/auth/register       — Đăng ký (OWNER / TENANT)
 *   POST /api/auth/login          — Đăng nhập
 *   POST /api/auth/refresh        — Lấy access token mới bằng refresh token (cookie)
 *   POST /api/auth/logout         — Logout thiết bị hiện tại
 *   POST /api/auth/logout-all     — Logout tất cả thiết bị
 *   GET  /api/auth/me             — Thông tin user hiện tại
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    // ==================== Register ====================

    /**
     * POST /api/auth/register
     * Body: { "email", "password", "fullName", "phone", "role" }
     * Role chỉ chấp nhận: ROLE_OWNER, ROLE_TENANT
     *
     * Response:
     *   - Body: accessToken + user info
     *   - Cookie: refreshToken (HttpOnly)
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<LoginResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthService.AuthResult result = authService.register(request);
        return buildAuthResponse(result, HttpStatus.CREATED, "Registration successful");
    }

    // ==================== Login ====================

    /**
     * POST /api/auth/login
     * Body: { "email", "password" }
     *
     * Response:
     *   - Body: accessToken + user info
     *   - Cookie: refreshToken (HttpOnly, 7 ngày)
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthService.AuthResult result = authService.login(request);
        return buildAuthResponse(result, HttpStatus.OK, "Login successful");
    }

    // ==================== Refresh Token ====================

    /**
     * POST /api/auth/refresh
     * Cookie: refreshToken (tự động gửi bởi browser)
     *
     * Response:
     *   - Body: accessToken mới
     *   - Cookie: refreshToken mới (Rotation — token cũ bị xóa)
     *
     * FE không cần gửi gì trong body — browser tự gửi cookie.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @CookieValue(name = RefreshTokenService.REFRESH_TOKEN_COOKIE_NAME, required = false)
            String refreshTokenValue
    ) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token not found. Please login again."));
        }

        AuthService.AuthResult result = authService.refresh(refreshTokenValue);
        return buildAuthResponse(result, HttpStatus.OK, "Token refreshed successfully");
    }

    // ==================== Logout ====================

    /**
     * POST /api/auth/logout
     * Cookie: refreshToken
     *
     * Logout thiết bị hiện tại — xóa refresh token này.
     * Cookie bị xóa phía client bằng Set-Cookie maxAge=0.
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenService.REFRESH_TOKEN_COOKIE_NAME, required = false)
            String refreshTokenValue
    ) {
        authService.logout(refreshTokenValue);

        // Xóa cookie phía client
        ResponseCookie clearCookie = refreshTokenService.buildClearRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success("Logged out successfully", null));
    }

    /**
     * POST /api/auth/logout-all
     *
     * Logout tất cả thiết bị — xóa hết refresh token của user trong DB.
     */
    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @CookieValue(name = RefreshTokenService.REFRESH_TOKEN_COOKIE_NAME, required = false)
            String refreshTokenValue
    ) {
        User user = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));

        authService.logoutAll(user);

        // Xóa cookie thiết bị hiện tại
        ResponseCookie clearCookie = refreshTokenService.buildClearRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success("Logged out from all devices successfully", null));
    }

    // ==================== Me ====================

    /**
     * GET /api/auth/me
     * Header: Authorization: Bearer <accessToken>
     *
     * Dùng để FE kiểm tra token còn hợp lệ và lấy thông tin user.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getCurrentUser() {
        User user = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));

        String primaryRole = user.getRoles().stream()
                .map(fu.stockspace.stockspace_be.auth.entity.Role::getName)
                .findFirst()
                .orElse("");

        UserInfoResponse info = new UserInfoResponse(
                user.getId().toString(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                primaryRole,
                user.isActive(),
                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null
        );

        return ResponseEntity.ok(ApiResponse.success("User info retrieved", info));
    }

    // ==================== Private helpers ====================

    /**
     * Build response với accessToken trong body + refreshToken trong HttpOnly Cookie.
     */
    private ResponseEntity<ApiResponse<LoginResponse>> buildAuthResponse(
            AuthService.AuthResult result,
            HttpStatus status,
            String message
    ) {
        ResponseCookie refreshCookie = refreshTokenService.buildRefreshTokenCookie(result.refreshTokenValue());

        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(message, result.loginResponse()));
    }

    // ==================== Inner records ====================

    public record UserInfoResponse(
            String userId,
            String email,
            String fullName,
            String phone,
            String role,
            boolean isActive,
            String createdAt
    ) {}
}

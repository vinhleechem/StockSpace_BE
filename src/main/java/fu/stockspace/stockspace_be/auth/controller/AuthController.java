package fu.stockspace.stockspace_be.auth.controller;

import fu.stockspace.stockspace_be.auth.dto.*;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.service.AuthService;
import fu.stockspace.stockspace_be.auth.service.RefreshTokenService;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;















@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, quên mật khẩu, Google OAuth")
public class AuthController {

        private final AuthService authService;
        private final RefreshTokenService refreshTokenService;
        private final fu.stockspace.stockspace_be.staff.service.TenantStaffService tenantStaffService;




        @PostMapping("/register")
        @Operation(summary = "Đăng ký tài khoản mới (OWNER / TENANT). Gửi email chào mừng sau khi đăng ký thành công.")
        public ResponseEntity<ApiResponse<LoginResponse>> register(
                        @Valid @RequestBody RegisterRequest request) {
                AuthService.AuthResult result = authService.register(request);
                return buildAuthResponse(result, HttpStatus.CREATED, "Registration successful");
        }



        @PostMapping("/login")
        @Operation(summary = "Đăng nhập bằng email và mật khẩu")
        public ResponseEntity<ApiResponse<LoginResponse>> login(
                        @Valid @RequestBody LoginRequest request) {
                AuthService.AuthResult result = authService.login(request);
                return buildAuthResponse(result, HttpStatus.OK, "Login successful");
        }









        @PostMapping("/google")
        @Operation(summary = "Đăng nhập / Đăng ký bằng Google OAuth. FE gửi authorization code lên.")
        public ResponseEntity<ApiResponse<LoginResponse>> googleLogin(
                        @Valid @RequestBody GoogleLoginRequest request) {
                AuthService.AuthResult result = authService.loginWithGoogle(request.getCode(), request.getRole());
                return buildAuthResponse(result, HttpStatus.OK, "Google login successful");
        }










        @PostMapping("/forgot-password")
        @Operation(summary = "Yêu cầu đặt lại mật khẩu — gửi đường dẫn đặt lại mật khẩu về email")
        public ResponseEntity<ApiResponse<Void>> forgotPassword(
                        @Valid @RequestBody ForgotPasswordRequest request) {
                authService.forgotPassword(request.getEmail());
                return ResponseEntity.ok(ApiResponse.success(
                                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi đường dẫn đặt lại mật khẩu đến hộp thư của bạn.",
                                null));
        }





        @PostMapping("/reset-password")
        @Operation(summary = "Đặt lại mật khẩu bằng mã token nhận qua email")
        public ResponseEntity<ApiResponse<Void>> resetPassword(
                        @Valid @RequestBody ResetPasswordRequest request) {
                authService.resetPassword(request);
                return ResponseEntity.ok(ApiResponse.success(
                                "Mật khẩu đã được đặt lại thành công. Vui lòng đăng nhập lại.", null));
        }



        @GetMapping("/staff/invite")
        @Operation(summary = "Xem trước thông tin lời mời nhân viên kho từ token trong email")
        public ResponseEntity<ApiResponse<fu.stockspace.stockspace_be.staff.dto.InvitationPreviewResponse>> previewStaffInvitation(
                        @RequestParam String token) {
                fu.stockspace.stockspace_be.staff.dto.InvitationPreviewResponse response = tenantStaffService
                                .previewInvitation(token);
                if (!response.isValid()) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                        .body(ApiResponse.error(response.getMessage()));
                }
                return ResponseEntity.ok(ApiResponse.success("Xác thực token lời mời thành công", response));
        }

        @PostMapping("/staff/accept")
        @Operation(summary = "Nhân viên kho thiết lập mật khẩu và xác nhận tham gia tổ chức")
        public ResponseEntity<ApiResponse<Void>> acceptStaffInvitation(
                        @Valid @RequestBody fu.stockspace.stockspace_be.staff.dto.AcceptInvitationRequest request) {
                tenantStaffService.acceptInvitation(request);
                return ResponseEntity.ok(ApiResponse.success(
                                "Xác nhận tham gia và thiết lập mật khẩu thành công. Vui lòng đăng nhập lại.", null));
        }



        @PostMapping("/refresh")
        @Operation(summary = "Lấy access token mới bằng refresh token (cookie)")
        public ResponseEntity<ApiResponse<LoginResponse>> refresh(
                        @Parameter(hidden = true) @CookieValue(name = RefreshTokenService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshTokenValue) {
                if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                        .body(ApiResponse.error("Refresh token not found. Please login again."));
                }

                AuthService.AuthResult result = authService.refresh(refreshTokenValue);
                return buildAuthResponse(result, HttpStatus.OK, "Token refreshed successfully");
        }



        @PostMapping("/logout")
        @PreAuthorize("@rbac.hasPermission('AUTH_SESSION_MANAGE')")
        @Operation(summary = "Logout khỏi thiết bị hiện tại")
        public ResponseEntity<ApiResponse<Void>> logout(
                        @Parameter(hidden = true) @CookieValue(name = RefreshTokenService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshTokenValue) {
                authService.logout(refreshTokenValue);
                ResponseCookie clearCookie = refreshTokenService.buildClearRefreshTokenCookie();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                                .body(ApiResponse.success("Logged out successfully", null));
        }

        @PostMapping("/logout-all")
        @PreAuthorize("@rbac.hasPermission('AUTH_SESSION_MANAGE')")
        @Operation(summary = "Logout khỏi tất cả thiết bị")
        public ResponseEntity<ApiResponse<Void>> logoutAll(
                        @CookieValue(name = RefreshTokenService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshTokenValue) {
                User user = SecurityUtil.getCurrentUser()
                                .orElseThrow(() -> new IllegalStateException("Not authenticated"));

                authService.logoutAll(user);
                ResponseCookie clearCookie = refreshTokenService.buildClearRefreshTokenCookie();

                return ResponseEntity.ok()
                                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                                .body(ApiResponse.success("Logged out from all devices successfully", null));
        }



        @GetMapping("/me")
        @PreAuthorize("@rbac.hasPermission('PROFILE_READ')")
        @Operation(summary = "Lấy thông tin user đang đăng nhập")
        public ResponseEntity<ApiResponse<UserInfoResponse>> getCurrentUser() {
                User user = SecurityUtil.getCurrentUser()
                                .orElseThrow(() -> new IllegalStateException("Not authenticated"));

                String primaryRole = user.getRoles().stream()
                                .map(fu.stockspace.stockspace_be.auth.entity.Role::getName)
                                .findFirst()
                                .orElse("");

                UUID tenantId = null;
                try {
                        tenantId = fu.stockspace.stockspace_be.auth.util.TenantContextUtil.getCurrentTenantId();
                } catch (Exception e) {

                }

                UserInfoResponse info = new UserInfoResponse(
                                user.getId(),
                                user.getEmail(),
                                user.getFullName(),
                                user.getPhone(),
                                primaryRole,
                                user.getProvider() != null ? user.getProvider().name() : "LOCAL",
                                user.getAvatarUrl(),
                                user.isActive(),
                                user.getCreatedAt() != null ? user.getCreatedAt().toString() : null,
                                tenantId);

                return ResponseEntity.ok(ApiResponse.success("User info retrieved", info));
        }



        private ResponseEntity<ApiResponse<LoginResponse>> buildAuthResponse(
                        AuthService.AuthResult result,
                        HttpStatus status,
                        String message) {
                ResponseCookie refreshCookie = refreshTokenService.buildRefreshTokenCookie(result.refreshTokenValue());

                return ResponseEntity.status(status)
                                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                                .body(ApiResponse.success(message, result.loginResponse()));
        }



        public record UserInfoResponse(
                        UUID userId,
                        String email,
                        String fullName,
                        String phone,
                        String role,
                        String provider,
                        String avatarUrl,
                        boolean isActive,
                        String createdAt,
                        UUID tenantId) {
        }
}

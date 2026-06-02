package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.entity.RefreshToken;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RefreshTokenRepository;
import fu.stockspace.stockspace_be.common.exception.UnauthorizedException;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service quản lý toàn bộ vòng đời của Refresh Token.
 *
 * Refresh Token được lưu trong DB và gửi về client qua HttpOnly Cookie.
 * Client không thể đọc token này bằng JavaScript → chống XSS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    // Tên cookie — FE không cần biết tên này vì browser tự gửi
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    // ==================== Tạo token ====================

    /**
     * Tạo refresh token mới cho user và lưu vào DB.
     * Mỗi lần login tạo 1 token mới → hỗ trợ multi-device.
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())  // Random UUID làm token value
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    // ==================== Validate token ====================

    /**
     * Validate refresh token từ cookie.
     * Throws exception nếu không tìm thấy hoặc đã hết hạn.
     *
     * @return RefreshToken entity nếu hợp lệ
     */
    @Transactional
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshToken.isExpired()) {
            // Token hết hạn → xóa khỏi DB luôn
            refreshTokenRepository.delete(refreshToken);
            throw new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token has expired. Please login again.");
        }

        return refreshToken;
    }

    // ==================== Xóa token (Logout) ====================

    /**
     * Logout 1 thiết bị — xóa token cụ thể.
     */
    @Transactional
    public void deleteToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    /**
     * Logout tất cả thiết bị — xóa hết token của user.
     */
    @Transactional
    public void deleteAllTokensForUser(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        log.info("Logged out all devices for user: {}", user.getEmail());
    }

    // ==================== Cookie builder ====================

    /**
     * Tạo HttpOnly Cookie chứa refresh token — gửi kèm response.
     *
     * Cấu hình:
     * - HttpOnly: true → JS không đọc được
     * - Secure: false (dev) → true khi deploy HTTPS
     * - SameSite: Strict → chống CSRF
     * - Path: /api/auth → chỉ gửi khi gọi auth endpoints
     */
    public ResponseCookie buildRefreshTokenCookie(String tokenValue) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(false)        // ⚠️ Đổi thành true khi deploy production (HTTPS)
                .path("/api/auth")    // Cookie chỉ gửi kèm request đến /api/auth/*
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .sameSite("Strict")
                .build();
    }

    /**
     * Tạo cookie rỗng để xóa cookie phía client (dùng khi logout).
     */
    public ResponseCookie buildClearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/api/auth")
                .maxAge(0)           // maxAge = 0 → browser xóa cookie ngay
                .sameSite("Strict")
                .build();
    }
}

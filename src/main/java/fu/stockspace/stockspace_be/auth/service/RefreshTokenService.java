package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.entity.RefreshToken;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RefreshTokenRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;







@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Value("${app.security.cookie-secure:false}")
    private boolean secureCookie;


    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";







    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }









    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshToken.isExpired()) {

            refreshToken.setDeleted(true);
            refreshTokenRepository.save(refreshToken);
            throw new UnauthorizedException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token has expired. Please login again.");
        }

        if (refreshToken.getUser() == null
                || !refreshToken.getUser().isEnabled()
                || refreshToken.getUser().isDeleted()) {
            refreshToken.setDeleted(true);
            refreshTokenRepository.save(refreshToken);
            throw new UnauthorizedException(
                    ErrorCode.INVALID_REFRESH_TOKEN,
                    "Tài khoản không còn hoạt động. Vui lòng đăng nhập lại."
            );
        }

        return refreshToken;
    }






    @Transactional
    public void deleteToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }




    @Transactional
    public void deleteAllTokensForUser(User user) {
        refreshTokenRepository.deleteAllByUser(user);
        log.info("Logged out all devices for user: {}", user.getEmail());
    }












    public ResponseCookie buildRefreshTokenCookie(String tokenValue) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth")
                .maxAge(Duration.ofMillis(refreshExpirationMs))
                .sameSite("Strict")
                .build();
    }




    public ResponseCookie buildClearRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/api/auth")
                .maxAge(0)
                .sameSite("Strict")
                .build();
    }
}

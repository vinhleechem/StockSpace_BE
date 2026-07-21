package fu.stockspace.stockspace_be.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class xử lý toàn bộ JWT operations.
 *
 * Chỉ Dev 1 sửa file này.
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    // ==================== Generate Token ====================

    /**
     * Tạo JWT token từ UserDetails (mặc định — không có tenantId riêng, dùng cho TENANT/OWNER/ADMIN).
     * Claims gồm: sub (email), userId, role, tenantId (= userId với Tenant/Owner).
     */
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, null);
    }

    /**
     * Tạo JWT token có thêm claim `tenantId`.
     * - Với Staff: tenantId = UUID của Tenant mà Staff đang làm việc cho.
     * - Với Tenant/Owner/Admin: gọi với tenantId = null → hệ thống tự set tenantId = userId.
     */
    public String generateToken(UserDetails userDetails, java.util.UUID tenantId) {
        Map<String, Object> extraClaims = new HashMap<>();

        if (userDetails instanceof fu.stockspace.stockspace_be.auth.entity.User user) {
            extraClaims.put("userId", user.getId().toString());

            String roles = user.getRoles().stream()
                    .map(fu.stockspace.stockspace_be.auth.entity.Role::getName)
                    .collect(java.util.stream.Collectors.joining(","));
            extraClaims.put("roles", roles);

            String primaryRole = user.getRoles().stream()
                    .map(fu.stockspace.stockspace_be.auth.entity.Role::getName)
                    .findFirst()
                    .orElse("");
            extraClaims.put("role", primaryRole);
            extraClaims.put("fullName", user.getFullName());

            // tenantId: nếu Staff → dùng tenantId được truyền vào; ngược lại → chính là userId
            java.util.UUID resolvedTenantId = (tenantId != null) ? tenantId : user.getId();
            extraClaims.put("tenantId", resolvedTenantId.toString());
        }

        return buildToken(extraClaims, userDetails);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())  // username = email
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ==================== Validate Token ====================

    /**
     * Validate token — kiểm tra signature, expiration, và username khớp.
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    // ==================== Extract Claims ====================

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public String extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", String.class));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    public String extractRoles(String token) {
        return extractClaim(token, claims -> claims.get("roles", String.class));
    }

    /**
     * Lấy tenantId từ JWT.
     * Với Tenant/Owner/Admin: bằng với userId.
     * Với Staff: UUID của Tenant mà Staff đang làm việc.
     */
    public String extractTenantId(String token) {
        return extractClaim(token, claims -> claims.get("tenantId", String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // ==================== Private helpers ====================

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private SecretKey getSigningKey() {
        // Dùng BASE64URL để hỗ trợ cả Base64 và Base64URL format (ký tự - và _)
        byte[] keyBytes = Decoders.BASE64URL.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

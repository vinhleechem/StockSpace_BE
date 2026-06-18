package fu.stockspace.stockspace_be.auth.util;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Utility để Dev 2 lấy thông tin user đang login từ SecurityContext.
 *
 * ============================================================
 * DÀNH CHO DEV 2 — Cách dùng trong Controller/Service:
 *
 *   UUID userId = SecurityUtil.getCurrentUserId();
 *   String email = SecurityUtil.getCurrentUserEmail();
 *   Role role = SecurityUtil.getCurrentRole();
 * ============================================================
 */
public class SecurityUtil {

    private SecurityUtil() {
        // Utility class — không instantiate
    }

    /**
     * Lấy User entity của người đang đăng nhập.
     * Trả về Optional.empty() nếu chưa authenticate.
     */
    public static Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * Lấy ID (UUID) của user đang login.
     *
     * @throws IllegalStateException nếu chưa authenticate (không nên xảy ra trên endpoint có @PreAuthorize)
     */
    public static UUID getCurrentUserId() {
        return getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }

    /**
     * Lấy email của user đang login.
     */
    public static String getCurrentUserEmail() {
        return getCurrentUser()
                .map(User::getEmail)
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }

    /**
     * Lấy Role của user đang login (lấy role đầu tiên trong danh sách làm primary role).
     */
    public static Role getCurrentRole() {
        return getCurrentUser()
                .map(user -> user.getRoles().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("User has no roles assigned")))
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }

    /**
     * Lấy full name của user đang login.
     */
    public static String getCurrentFullName() {
        return getCurrentUser()
                .map(User::getFullName)
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }

    /**
     * Kiểm tra user hiện tại có role cụ thể không.
     */
    public static boolean hasRole(Role role) {
        if (role == null) return false;
        return getCurrentUser()
                .map(user -> user.getRoles().stream()
                        .anyMatch(r -> r.getName().equals(role.getName())))
                .orElse(false);
    }

    /**
     * Kiểm tra đã authenticate chưa.
     */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String);
    }
}

package fu.stockspace.stockspace_be.auth.util;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;












public class SecurityUtil {

    private SecurityUtil() {

    }





    public static Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }






    public static UUID getCurrentUserId() {
        return getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }




    public static String getCurrentUserEmail() {
        return getCurrentUser()
                .map(User::getEmail)
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }




    public static Role getCurrentRole() {
        return getCurrentUser()
                .map(user -> user.getRoles().stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException("User has no roles assigned")))
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }




    public static String getCurrentFullName() {
        return getCurrentUser()
                .map(User::getFullName)
                .orElseThrow(() -> new IllegalStateException("No authenticated user found in SecurityContext"));
    }




    public static boolean hasRole(Role role) {
        if (role == null) return false;
        return getCurrentUser()
                .map(user -> user.getRoles().stream()
                        .anyMatch(r -> r.getName().equals(role.getName())))
                .orElse(false);
    }




    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String);
    }
}

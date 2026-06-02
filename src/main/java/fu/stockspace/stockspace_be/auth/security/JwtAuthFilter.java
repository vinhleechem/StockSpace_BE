package fu.stockspace.stockspace_be.auth.security;

import fu.stockspace.stockspace_be.auth.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter chạy một lần mỗi request — đọc JWT từ header và set SecurityContext.
 *
 * Flow: Request → JwtAuthFilter → SecurityConfig rules → Controller
 *
 * Chỉ Dev 1 sửa file này.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Nếu không có header hoặc không phải Bearer token → skip
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7); // Bỏ "Bearer " prefix
        String userEmail = null;

        try {
            userEmail = jwtUtil.extractEmail(jwt);
        } catch (Exception e) {
            log.warn("Cannot extract email from JWT: {}", e.getMessage());
        }

        // Nếu có email và chưa authenticate trong context hiện tại
        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            if (jwtUtil.validateToken(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Set vào SecurityContext — từ đây @PreAuthorize sẽ hoạt động
                SecurityContextHolder.getContext().setAuthentication(authToken);

                log.debug("Authenticated user: {}, role: {}", userEmail, userDetails.getAuthorities());
            }
        }

        filterChain.doFilter(request, response);
    }
}

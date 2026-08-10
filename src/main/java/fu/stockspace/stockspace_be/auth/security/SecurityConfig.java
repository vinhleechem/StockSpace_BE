package fu.stockspace.stockspace_be.auth.security;

import fu.stockspace.stockspace_be.auth.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.beans.factory.annotation.Value;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security configuration — Spring Boot 4.x / Security 6.x style.
 *
 * ⚠️ QUAN TRỌNG: Chỉ Dev 1 được sửa file này!
 * Dev 2 chỉ dùng @PreAuthorize trên controller method, không sửa file này.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Bật @PreAuthorize, @PostAuthorize, @Secured
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * SecurityFilterChain — định nghĩa rules cho từng endpoint.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Tắt CSRF vì dùng JWT (stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // CORS config — cho phép frontend gọi API
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Phân quyền endpoint
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints — public hoàn toàn
                        .requestMatchers("/api/auth/**").permitAll()
                        
                        // Cụ thể permit Google Callback
                        .requestMatchers(HttpMethod.GET, "/api/auth/google/callback").permitAll()

                        // Swagger UI & OpenApi docs & Actuator health — public để test và healthcheck
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/actuator/**").permitAll()

                        // Warehouse search/view — public (Guest có thể xem)
                        .requestMatchers(HttpMethod.GET, "/api/warehouses/**").permitAll()

                        // Public configurations — public (Guest can view system configs like deposit percentage, etc.)
                        .requestMatchers(HttpMethod.GET, "/api/configs/**").permitAll()

                        // Active system policy is displayed before registration / booking.
                        .requestMatchers(HttpMethod.GET, "/api/system-policies/**").permitAll()

                        // Public packages — public
                        .requestMatchers(HttpMethod.GET, "/api/packages/**").permitAll()

                        // Guest Chatbot
                        .requestMatchers("/api/chat/guest/**").permitAll()

                        // Browser handshake is public; the STOMP CONNECT frame is JWT-authenticated.
                        .requestMatchers("/ws", "/ws/**").permitAll()

                        // Tất cả còn lại cần authenticate
                        .anyRequest().authenticated()
                )

                // Stateless session — không dùng session, dùng JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authentication provider
                .authenticationProvider(authenticationProvider())

                // Thêm JWT filter TRƯỚC UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration — cho phép frontend (React) gọi API.
     * Sửa origins khi deploy production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        List<String> origins = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "https://stock-space-nu.vercel.app"
        ));
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            String cleanUrl = frontendUrl.trim().replaceAll("/+$", "");
            if (!origins.contains(cleanUrl)) {
                origins.add(cleanUrl);
            }
        }

        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * Authentication provider dùng DB + BCrypt.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        // Spring Security 6.x (Spring Boot 4.x): truyền UserDetailsService qua constructor
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * BCrypt password encoder — strength 12 là đủ secure và không quá chậm.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * AuthenticationManager — inject vào AuthService để authenticate.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

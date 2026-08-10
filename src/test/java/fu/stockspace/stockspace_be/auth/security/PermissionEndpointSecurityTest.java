package fu.stockspace.stockspace_be.auth.security;

import fu.stockspace.stockspace_be.stats.dto.PlatformSummaryResponse;
import fu.stockspace.stockspace_be.stats.service.AdminStatsService;
import fu.stockspace.stockspace_be.stats.controller.AdminStatsController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = PermissionEndpointSecurityTest.MethodSecurityTestConfig.class)
class PermissionEndpointSecurityTest {

    @Autowired
    private AdminStatsController adminStatsController;

    @Autowired
    private AdminStatsService adminStatsService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesUnauthenticatedCallBeforeTheControllerRuns() {
        assertThrows(AccessDeniedException.class, () -> adminStatsController.getPlatformSummary());
    }

    @Test
    void deniesAuthenticatedUserWithoutTheRequiredPermission() {
        authenticate("owner@example.com", "OWNER_STATS_READ");

        assertThrows(AccessDeniedException.class, () -> adminStatsController.getPlatformSummary());
    }

    @Test
    void allowsOnlyTheExactPermission() {
        authenticate("admin@example.com", "ADMIN_STATS_READ");
        PlatformSummaryResponse response = PlatformSummaryResponse.builder().totalUsers(3L).build();
        when(adminStatsService.getPlatformSummary()).thenReturn(response);

        assertDoesNotThrow(() -> adminStatsController.getPlatformSummary());
        verify(adminStatsService).getPlatformSummary();
    }

    private void authenticate(String principal, String... authorities) {
        List<SimpleGrantedAuthority> granted = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "N/A", granted));
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        RbacAuthorization rbac() {
            return new RbacAuthorization();
        }

        @Bean
        AdminStatsService adminStatsService() {
            return mock(AdminStatsService.class);
        }

        @Bean
        AdminStatsController adminStatsController(AdminStatsService adminStatsService) {
            return new AdminStatsController(adminStatsService);
        }
    }
}

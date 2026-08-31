package fu.stockspace.stockspace_be.auth.controller;

import fu.stockspace.stockspace_be.auth.dto.UpdateProfileRequest;
import fu.stockspace.stockspace_be.auth.entity.AuthProvider;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.security.RbacAuthorization;
import fu.stockspace.stockspace_be.auth.service.AuthService;
import fu.stockspace.stockspace_be.auth.service.ProfileService;
import fu.stockspace.stockspace_be.auth.service.RefreshTokenService;
import fu.stockspace.stockspace_be.staff.service.TenantStaffService;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AuthControllerProfileSecurityTest.TestConfig.class)
@TestPropertySource(properties = {
        "app.jwt.refresh-expiration-ms=3600000",
        "app.google.client-id=test-client",
        "app.google.client-secret=test-secret",
        "app.google.redirect-uri=http://localhost/callback"
})
class AuthControllerProfileSecurityTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private ProfileService profileService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateCurrentUser_deniesUnauthenticatedCall() {
        assertThrows(AccessDeniedException.class, () -> authController.updateCurrentUser(request()));
    }

    @Test
    void updateCurrentUser_requiresProfileUpdatePermission() {
        authenticate(currentUser(), "PROFILE_READ");

        assertThrows(AccessDeniedException.class, () -> authController.updateCurrentUser(request()));
    }

    @Test
    void updateCurrentUser_updatesOnlyAuthenticatedUser() {
        User currentUser = currentUser();
        User updatedUser = User.builder()
                .id(currentUser.getId())
                .email(currentUser.getEmail())
                .fullName("Updated User")
                .phone("0987654321")
                .avatarUrl("https://cdn.example.com/avatar.png")
                .provider(AuthProvider.LOCAL)
                .roles(currentUser.getRoles())
                .isActive(true)
                .build();
        UpdateProfileRequest request = request();
        authenticate(currentUser, "PROFILE_UPDATE");
        when(profileService.updateProfile(currentUser.getId(), request)).thenReturn(updatedUser);

        var response = authController.updateCurrentUser(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(currentUser.getId(), response.getBody().getData().userId());
        assertEquals("Updated User", response.getBody().getData().fullName());
        assertEquals("tenant@example.com", response.getBody().getData().email());
        verify(profileService).updateProfile(currentUser.getId(), request);
    }

    private User currentUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("tenant@example.com")
                .fullName("Current User")
                .roles(Set.of(Role.builder().name("ROLE_TENANT").build()))
                .isActive(true)
                .build();
    }

    private UpdateProfileRequest request() {
        return UpdateProfileRequest.builder()
                .fullName("Updated User")
                .phone("0987654321")
                .avatarUrl("https://cdn.example.com/avatar.png")
                .build();
    }

    private void authenticate(User principal, String authority) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        "N/A",
                        List.of(new SimpleGrantedAuthority(authority))));
    }

    @Configuration
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        RbacAuthorization rbac() {
            return new RbacAuthorization();
        }

        @Bean
        AuthService authService() {
            return mock(AuthService.class);
        }

        @Bean
        ProfileService profileService() {
            return mock(ProfileService.class);
        }

        @Bean
        RefreshTokenService refreshTokenService() {
            return mock(RefreshTokenService.class);
        }

        @Bean
        TenantStaffService tenantStaffService() {
            return mock(TenantStaffService.class);
        }

        @Bean
        AuthController authController(AuthService authService,
                                      ProfileService profileService,
                                      RefreshTokenService refreshTokenService,
                                      TenantStaffService tenantStaffService) {
            return new AuthController(authService, profileService, refreshTokenService, tenantStaffService);
        }
    }
}

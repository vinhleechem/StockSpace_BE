package fu.stockspace.stockspace_be.auth.controller;

import fu.stockspace.stockspace_be.auth.dto.RegisterRequest;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.service.AuthService;
import fu.stockspace.stockspace_be.auth.service.ProfileService;
import fu.stockspace.stockspace_be.auth.service.RefreshTokenService;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.staff.service.TenantStaffService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerRegistrationTest {

    @Test
    void registerReturnsCreatedResponseWithoutAuthenticationCookie() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(
                authService,
                mock(ProfileService.class),
                mock(RefreshTokenService.class),
                mock(TenantStaffService.class)
        );

        RegisterRequest request = request();
        var response = controller.register(request);
        ApiResponse<Void> body = response.getBody();

        assertEquals(201, response.getStatusCode().value());
        assertEquals("Registration successful", body.getMessage());
        assertTrue(body.isSuccess());
        assertNull(body.getData());
        assertNull(response.getHeaders().getFirst("Set-Cookie"));
        verify(authService).register(request);
    }

    private RegisterRequest request() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new-user@example.com");
        request.setPassword("password123");
        request.setFullName("New User");
        request.setPhone("0912345678");
        request.setRole(RoleType.ROLE_OWNER);
        return request;
    }
}

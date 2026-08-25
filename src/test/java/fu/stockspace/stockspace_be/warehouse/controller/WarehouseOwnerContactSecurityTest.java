package fu.stockspace.stockspace_be.warehouse.controller;

import fu.stockspace.stockspace_be.auth.security.RbacAuthorization;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseOwnerContactResponse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseTypeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = WarehouseOwnerContactSecurityTest.MethodSecurityTestConfig.class)
class WarehouseOwnerContactSecurityTest {

    @Autowired
    private PublicWarehouseController publicWarehouseController;

    @Autowired
    private WarehouseService warehouseService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesUnauthenticatedContactRequest() {
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> publicWarehouseController.getOwnerContact(UUID.randomUUID()));
    }

    @Test
    void allowsAnyAuthenticatedRegisteredUserToRequestContact() {
        UUID warehouseId = UUID.randomUUID();
        authenticate("tenant@example.com");
        WarehouseOwnerContactResponse response = WarehouseOwnerContactResponse.builder()
                .warehouseId(warehouseId)
                .ownerId(UUID.randomUUID())
                .ownerName("Owner")
                .phone("0987654321")
                .build();
        when(warehouseService.getOwnerContact(warehouseId)).thenReturn(response);

        assertDoesNotThrow(() -> publicWarehouseController.getOwnerContact(warehouseId));
        verify(warehouseService).getOwnerContact(warehouseId);
    }

    private void authenticate(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        "N/A",
                        List.of(new SimpleGrantedAuthority("ROLE_TENANT"))));
    }

    @Configuration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        RbacAuthorization rbac() {
            return new RbacAuthorization();
        }

        @Bean
        WarehouseService warehouseService() {
            return mock(WarehouseService.class);
        }

        @Bean
        WarehouseTypeService warehouseTypeService() {
            return mock(WarehouseTypeService.class);
        }

        @Bean
        WarehouseLayoutService warehouseLayoutService() {
            return mock(WarehouseLayoutService.class);
        }

        @Bean
        PublicWarehouseController publicWarehouseController(
                WarehouseService warehouseService,
                WarehouseTypeService warehouseTypeService,
                WarehouseLayoutService warehouseLayoutService) {
            return new PublicWarehouseController(
                    warehouseService, warehouseTypeService, warehouseLayoutService);
        }
    }
}

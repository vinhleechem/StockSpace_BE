package fu.stockspace.stockspace_be.common.config;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RefreshTokenRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionLegacyDemoAccountGuardTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void disablesAndRevokesOnlyAccountStillUsingKnownDemoPasswordWithoutChangingIt() {
        User legacyAdmin = User.builder()
                .email("admin@stockspace.com")
                .password("legacy-hash")
                .isActive(true)
                .build();
        when(userRepository.findByEmail("admin@stockspace.com"))
                .thenReturn(Optional.of(legacyAdmin));
        when(userRepository.findByEmail(
                org.mockito.ArgumentMatchers.argThat(
                        email -> !"admin@stockspace.com".equals(email))))
                .thenReturn(Optional.empty());
        when(passwordEncoder.matches("Password123", "legacy-hash"))
                .thenReturn(true);
        new ProductionLegacyDemoAccountGuard(
                userRepository,
                refreshTokenRepository,
                passwordEncoder
        ).run(new DefaultApplicationArguments(new String[0]));

        assertFalse(legacyAdmin.isActive());
        assertEquals("legacy-hash", legacyAdmin.getPassword());
        verify(userRepository).save(legacyAdmin);
        verify(refreshTokenRepository).deleteAllByUser(legacyAdmin);
    }

    @Test
    void leavesAccountAloneAfterItsPasswordWasChanged() {
        User admin = User.builder()
                .email("admin@stockspace.com")
                .password("changed-hash")
                .isActive(true)
                .build();
        when(userRepository.findByEmail("admin@stockspace.com"))
                .thenReturn(Optional.of(admin));
        when(userRepository.findByEmail(
                org.mockito.ArgumentMatchers.argThat(
                        email -> !"admin@stockspace.com".equals(email))))
                .thenReturn(Optional.empty());
        when(passwordEncoder.matches("Password123", "changed-hash"))
                .thenReturn(false);

        new ProductionLegacyDemoAccountGuard(
                userRepository,
                refreshTokenRepository,
                passwordEncoder
        ).run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).save(admin);
        verify(refreshTokenRepository, never()).deleteAllByUser(admin);
    }
}

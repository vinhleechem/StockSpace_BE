package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.entity.RefreshToken;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RefreshTokenRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    @Test
    void expiredTokenIsRevokedAndRevocationCommitsWithExpectedException() throws Exception {
        User user = User.builder().email("user@example.com").isActive(true).build();
        RefreshToken token = RefreshToken.builder()
                .token("expired")
                .user(user)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(repository.findByToken("expired")).thenReturn(Optional.of(token));
        RefreshTokenService service = new RefreshTokenService(repository);

        assertThrows(
                UnauthorizedException.class,
                () -> service.validateRefreshToken("expired")
        );

        assertTrue(token.isDeleted());
        verify(repository).save(token);
        Method method = RefreshTokenService.class
                .getMethod("validateRefreshToken", String.class);
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertTrue(Arrays.asList(transactional.noRollbackFor())
                .contains(UnauthorizedException.class));
    }
}

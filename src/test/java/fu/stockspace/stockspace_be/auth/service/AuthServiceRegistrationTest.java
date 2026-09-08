package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.dto.RegisterRequest;
import fu.stockspace.stockspace_be.auth.entity.AuthProvider;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.PasswordResetTokenRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.security.JwtUtil;
import fu.stockspace.stockspace_be.auth.httpclient.OutboundAuthClient;
import fu.stockspace.stockspace_be.auth.httpclient.OutboundUserClient;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceRegistrationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private OutboundAuthClient outboundAuthClient;

    @Mock
    private OutboundUserClient outboundUserClient;

    @Mock
    private fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository tenantMemberRepository;

    @Mock
    private WalletService walletService;

    @Test
    void registerCreatesAccountAndWalletWithoutCreatingSession() {
        RegisterRequest request = request(RoleType.ROLE_OWNER);
        Role role = Role.builder().name(RoleType.ROLE_OWNER.name()).build();
        UUID userId = UUID.randomUUID();
        User savedUser = User.builder()
                .id(userId)
                .email(request.getEmail())
                .fullName(request.getFullName())
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByName(RoleType.ROLE_OWNER.name())).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        doNothing().when(emailService).sendWelcomeEmail(request.getEmail(), request.getFullName());

        service().register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User userToSave = userCaptor.getValue();
        assertEquals(request.getEmail(), userToSave.getEmail());
        assertEquals("encoded-password", userToSave.getPassword());
        assertEquals(AuthProvider.LOCAL, userToSave.getProvider());
        assertTrue(userToSave.isActive());
        assertEquals(role, userToSave.getRoles().iterator().next());
        verify(walletService).getOrCreateWallet(userId);
        verify(emailService).sendWelcomeEmail(request.getEmail(), request.getFullName());
        verifyNoInteractions(jwtUtil, refreshTokenService);
    }

    @Test
    void registerAcceptsTenantRole() {
        RegisterRequest request = request(RoleType.ROLE_TENANT);
        Role role = Role.builder().name(RoleType.ROLE_TENANT.name()).build();
        User savedUser = User.builder().id(UUID.randomUUID()).build();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByName(RoleType.ROLE_TENANT.name())).thenReturn(Optional.of(role));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        service().register(request);

        verify(roleRepository).findByName(RoleType.ROLE_TENANT.name());
    }

    @Test
    void registerRejectsUnsupportedSelfRegistrationRole() {
        RegisterRequest request = request(RoleType.ROLE_ADMIN);

        assertThrows(BadRequestException.class, () -> service().register(request));
        verifyNoInteractions(userRepository, roleRepository, passwordEncoder, walletService, emailService,
                jwtUtil, refreshTokenService);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = request(RoleType.ROLE_OWNER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service().register(request));
        verifyNoInteractions(roleRepository, passwordEncoder, walletService, emailService,
                jwtUtil, refreshTokenService);
    }

    @Test
    void registerRejectsMissingRoleRecord() {
        RegisterRequest request = request(RoleType.ROLE_OWNER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleRepository.findByName(RoleType.ROLE_OWNER.name())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service().register(request));
        verifyNoInteractions(passwordEncoder, walletService, emailService, jwtUtil, refreshTokenService);
    }

    private AuthService service() {
        return new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                jwtUtil,
                authenticationManager,
                refreshTokenService,
                emailService,
                passwordResetTokenRepository,
                outboundAuthClient,
                outboundUserClient,
                tenantMemberRepository,
                walletService
        );
    }

    private RegisterRequest request(RoleType role) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new-user@example.com");
        request.setPassword("password123");
        request.setFullName("New User");
        request.setPhone("0912345678");
        request.setRole(role);
        return request;
    }
}

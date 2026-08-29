package fu.stockspace.stockspace_be.auth.service;

import fu.stockspace.stockspace_be.auth.dto.*;
import fu.stockspace.stockspace_be.auth.entity.*;
import fu.stockspace.stockspace_be.auth.httpclient.OutboundAuthClient;
import fu.stockspace.stockspace_be.auth.httpclient.OutboundUserClient;
import fu.stockspace.stockspace_be.auth.repository.PasswordResetTokenRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.security.JwtUtil;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;





@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final OutboundAuthClient outboundAuthClient;
    private final OutboundUserClient outboundUserClient;
    private final fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository tenantMemberRepository;
    private final fu.stockspace.stockspace_be.wallet.service.WalletService walletService;

    @Value("${app.google.client-id}")
    private String googleClientId;

    @Value("${app.google.client-secret}")
    private String googleClientSecret;

    @Value("${app.google.redirect-uri}")
    private String googleRedirectUri;


    private static final Set<RoleType> SELF_REGISTER_ROLES = Set.of(RoleType.ROLE_OWNER, RoleType.ROLE_TENANT);

    private static final String GOOGLE_GRANT_TYPE = "authorization_code";
    private static final String GOOGLE_RESPONSE_FORMAT = "json";
    private static final int OTP_EXPIRY_MINUTES = 15;

    private final SecureRandom secureRandom = new SecureRandom();







    @Transactional
    public AuthResult register(RegisterRequest request) {
        if (!SELF_REGISTER_ROLES.contains(request.getRole())) {
            throw new BadRequestException(ErrorCode.ROLE_NOT_SUPPORTED);
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }

        Role dbRole = roleRepository.findByName(request.getRole().name())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .roles(Set.of(dbRole))
                .provider(AuthProvider.LOCAL)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {} with role {}", user.getEmail(), dbRole.getName());


        walletService.getOrCreateWallet(user.getId());


        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());

        return buildAuthResult(user);
    }






    @Transactional
    public AuthResult login(LoginRequest request) {

        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.GOOGLE) {
                throw new ResourceConflictException(ErrorCode.CANNOT_LOGIN_GOOGLE_WITH_PASSWORD);
            }
        });

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        String userRolesStr = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.joining(","));
        log.info("User logged in: {} with roles [{}]", user.getEmail(), userRolesStr);
        return buildAuthResult(user);
    }













    @Transactional
    public AuthResult loginWithGoogle(String code, String requestedRole) {
        try {

            MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
            tokenRequest.add("code", code);
            tokenRequest.add("client_id", googleClientId);
            tokenRequest.add("client_secret", googleClientSecret);
            tokenRequest.add("redirect_uri", googleRedirectUri);
            tokenRequest.add("grant_type", GOOGLE_GRANT_TYPE);

            ExchangeTokenResponse tokenResponse = outboundAuthClient.exchangeToken(tokenRequest);


            GoogleUserInfo userInfo = outboundUserClient.getUserInfo(GOOGLE_RESPONSE_FORMAT, tokenResponse.getAccessToken());
            log.info("Google OAuth: user info retrieved for email: {}", userInfo.getEmail());


            User user = userRepository.findByEmail(userInfo.getEmail())
                    .map(existing -> {
                        if (!existing.isActive()) {
                            throw new ResourceConflictException(ErrorCode.USER_LOCKED);
                        }

                        if (existing.getProvider() == AuthProvider.LOCAL) {
                            throw new ResourceConflictException(ErrorCode.CANNOT_LOGIN_PASSWORD_WITH_GOOGLE);
                        }

                        existing.setAvatarUrl(userInfo.getPicture());
                        return userRepository.save(existing);
                    })
                    .orElseGet(() -> {

                        RoleType roleType = RoleType.ROLE_OWNER.name().equals(requestedRole)
                                ? RoleType.ROLE_OWNER
                                : RoleType.ROLE_TENANT;
                        Role assignedRole = roleRepository.findByName(roleType.name())
                                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND));
                        log.info("Google OAuth: assigning role [{}] to new user", roleType.name());


                        String fullName = userInfo.getName() != null ? userInfo.getName()
                                : ((userInfo.getGivenName() != null ? userInfo.getGivenName() : "")
                                + " " + (userInfo.getFamilyName() != null ? userInfo.getFamilyName() : "")).trim();
                        if (fullName.isBlank()) fullName = userInfo.getEmail();

                        User newUser = User.builder()
                                .email(userInfo.getEmail())
                                .password(passwordEncoder.encode(generateRandomPassword()))
                                .fullName(fullName)
                                .avatarUrl(userInfo.getPicture())
                                .provider(AuthProvider.GOOGLE)
                                .roles(Set.of(assignedRole))
                                .isActive(true)
                                .build();

                        User saved = userRepository.save(newUser);
                        log.info("New Google user registered: {}", saved.getEmail());

                        walletService.getOrCreateWallet(saved.getId());

                        emailService.sendWelcomeEmail(saved.getEmail(), saved.getFullName());
                        return saved;
                    });

            return buildAuthResult(user);

        } catch (ResourceConflictException | ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google OAuth login failed: {}", e.getMessage(), e);
            throw new BadRequestException(ErrorCode.GOOGLE_AUTH_FAILED);
        }
    }







    @Transactional
    public void forgotPassword(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.getProvider() == AuthProvider.GOOGLE) {

                log.warn("Forgot password attempted for Google account: {}", email);
                return;
            }


            passwordResetTokenRepository.deleteAllByUser(user);


            String token = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .user(user)
                    .token(token)
                    .expiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                    .build();
            passwordResetTokenRepository.save(resetToken);


            emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), token);
            log.info("Password reset token sent for user: {}", email);
        });
    }




    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new BadRequestException(ErrorCode.INVALID_RESET_TOKEN);
        }

        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByUserAndToken(user, request.getToken())
                .orElseThrow(() -> new BadRequestException(ErrorCode.INVALID_RESET_TOKEN));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new BadRequestException(ErrorCode.RESET_TOKEN_EXPIRED);
        }


        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);


        passwordResetTokenRepository.delete(resetToken);


        refreshTokenService.deleteAllTokensForUser(user);

        log.info("Password reset successfully for user: {}", user.getEmail());
    }






    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String refreshTokenValue) {
        RefreshToken oldRefreshToken = refreshTokenService.validateRefreshToken(refreshTokenValue);
        User user = oldRefreshToken.getUser();
        if (!user.isEnabled() || user.isDeleted()) {
            refreshTokenService.deleteAllTokensForUser(user);
            throw new UnauthorizedException(ErrorCode.USER_LOCKED);
        }

        refreshTokenService.deleteToken(refreshTokenValue);

        log.info("Token refreshed for user: {}", user.getEmail());
        return buildAuthResult(user);
    }



    @Transactional
    public void logout(String refreshTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenService.deleteToken(refreshTokenValue);
        }
    }

    @Transactional
    public void logoutAll(User user) {
        refreshTokenService.deleteAllTokensForUser(user);
    }



    private AuthResult buildAuthResult(User user) {

        UUID tenantId = null;
        boolean isStaff = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals(fu.stockspace.stockspace_be.auth.entity.RoleType.ROLE_STAFF.name()));
        if (isStaff) {
            tenantId = tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(user.getId())
                    .map(member -> member.getTenant().getId())
                    .orElse(null);
        }

        String accessToken = jwtUtil.generateToken(user, tenantId);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        String primaryRole = user.getRoles().stream()
                .map(Role::getName)
                .findFirst()
                .orElse("");

        UUID resolvedTenantId = (tenantId != null) ? tenantId : user.getId();

        LoginResponse loginResponse = LoginResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(primaryRole)
                .tenantId(resolvedTenantId)
                .build();

        return new AuthResult(loginResponse, refreshToken.getToken());
    }


    private String generateRandomPassword() {
        return UUID.randomUUID().toString();
    }

    public record AuthResult(LoginResponse loginResponse, String refreshTokenValue) {}
}

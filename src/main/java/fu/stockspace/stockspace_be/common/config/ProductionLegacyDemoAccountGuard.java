package fu.stockspace.stockspace_be.common.config;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RefreshTokenRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Neutralizes demo credentials created by older releases without disabling a
 * legitimate account whose password has already been changed.
 */
@Slf4j
@Component
@Profile("prod")
@ConditionalOnProperty(
        name = "app.security.legacy-demo-account-guard-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ProductionLegacyDemoAccountGuard implements ApplicationRunner {

    private static final String LEGACY_PASSWORD = "Password123";
    private static final List<String> LEGACY_EMAILS = List.of(
            "admin@stockspace.com",
            "owner@stockspace.com",
            "tenant@stockspace.com",
            "staff@stockspace.com",
            "inspector@stockspace.com"
    );

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int disabled = 0;
        for (String email : LEGACY_EMAILS) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null || !stillUsesLegacyPassword(user)) {
                continue;
            }

            user.setActive(false);
            userRepository.save(user);
            refreshTokenRepository.deleteAllByUser(user);
            disabled++;
        }

        if (disabled > 0) {
            log.warn(
                    "Disabled {} legacy demo account(s) still using the public default password",
                    disabled
            );
        }
    }

    private boolean stillUsesLegacyPassword(User user) {
        try {
            return user.getPassword() != null
                    && passwordEncoder.matches(LEGACY_PASSWORD, user.getPassword());
        } catch (IllegalArgumentException invalidStoredHash) {
            return false;
        }
    }

}

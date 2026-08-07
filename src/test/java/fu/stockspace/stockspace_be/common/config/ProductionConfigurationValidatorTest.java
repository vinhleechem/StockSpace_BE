package fu.stockspace.stockspace_be.common.config;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigurationValidatorTest {

    private static final String STRONG_SECRET = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(new byte[64]);

    @Test
    void acceptsStrongConfiguredPrivateRouting() {
        ProductionConfigurationValidator validator =
                new ProductionConfigurationValidator(
                        STRONG_SECRET,
                        "sk-or-v1-test",
                        "provider/tool-model",
                        "deny",
                        true,
                        false,
                        1536
                );

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void rejectsMissingCredentialsWeakJwtAndNonPrivateRouting() {
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionConfigurationValidator(
                        "",
                        "key",
                        "model",
                        "deny",
                        true,
                        false,
                        1536
                ).validate()
        );
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionConfigurationValidator(
                        Base64.getUrlEncoder().encodeToString(new byte[16]),
                        "key",
                        "model",
                        "deny",
                        true,
                        false,
                        1536
                ).validate()
        );
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionConfigurationValidator(
                        STRONG_SECRET,
                        "key",
                        "model",
                        "allow",
                        false,
                        false,
                        1536
                ).validate()
        );
        assertThrows(
                IllegalStateException.class,
                () -> new ProductionConfigurationValidator(
                        STRONG_SECRET,
                        "key",
                        "model",
                        "deny",
                        true,
                        false,
                        768
                ).validate()
        );
    }

    @Test
    void allowsInsecureProviderRoutingOnlyWhenExplicitlyEnabled() {
        ProductionConfigurationValidator validator =
                new ProductionConfigurationValidator(
                        STRONG_SECRET,
                        "sk-or-v1-test",
                        "provider/free-model",
                        "allow",
                        false,
                        true,
                        1536
                );

        assertDoesNotThrow(validator::validate);
    }
}

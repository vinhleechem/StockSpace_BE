package fu.stockspace.stockspace_be.common.config;

import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails closed when a production deployment would start with missing signing
 * material or without the privacy routing expected for private chatbot tools.
 */
@Component
@Profile("prod")
public class ProductionConfigurationValidator {

    private final String jwtSecret;
    private final String openRouterApiKey;
    private final String openRouterModel;
    private final String dataCollection;
    private final boolean zeroDataRetention;
    private final int embeddingDimensions;

    public ProductionConfigurationValidator(
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.openrouter.api-key:}") String openRouterApiKey,
            @Value("${app.openrouter.model:}") String openRouterModel,
            @Value("${app.openrouter.data-collection:deny}") String dataCollection,
            @Value("${app.openrouter.zdr:true}") boolean zeroDataRetention,
            @Value("${app.openrouter.embedding-dimensions:1536}") int embeddingDimensions
    ) {
        this.jwtSecret = jwtSecret;
        this.openRouterApiKey = openRouterApiKey;
        this.openRouterModel = openRouterModel;
        this.dataCollection = dataCollection;
        this.zeroDataRetention = zeroDataRetention;
        this.embeddingDimensions = embeddingDimensions;
    }

    @PostConstruct
    void validate() {
        requireConfigured("JWT secret", jwtSecret);
        if (decodeSecret(jwtSecret.trim()).length < 32) {
            throw new IllegalStateException(
                    "JWT secret must contain at least 256 bits after Base64 decoding"
            );
        }

        requireConfigured("OpenRouter API key", openRouterApiKey);
        requireConfigured("OpenRouter model", openRouterModel);
        if (!"deny".equalsIgnoreCase(dataCollection) || !zeroDataRetention) {
            throw new IllegalStateException(
                    "Production chatbot requires OpenRouter data_collection=deny and ZDR=true"
            );
        }
        if (embeddingDimensions != SystemKnowledge.EMBEDDING_DIMENSIONS) {
            throw new IllegalStateException(
                    "Embedding dimensions must match pgvector schema: "
                            + SystemKnowledge.EMBEDDING_DIMENSIONS
            );
        }
    }

    private byte[] decodeSecret(String value) {
        try {
            return Decoders.BASE64URL.decode(value);
        } catch (IllegalArgumentException invalidBase64Url) {
            try {
                return Decoders.BASE64.decode(value);
            } catch (IllegalArgumentException invalidBase64) {
                throw new IllegalStateException(
                        "JWT secret must be valid Base64 or Base64URL",
                        invalidBase64
                );
            }
        }
    }

    private void requireConfigured(String name, String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (normalized.isBlank()
                || normalized.contains("change_me")
                || normalized.startsWith("your_")) {
            throw new IllegalStateException(name + " is required in production");
        }
    }
}

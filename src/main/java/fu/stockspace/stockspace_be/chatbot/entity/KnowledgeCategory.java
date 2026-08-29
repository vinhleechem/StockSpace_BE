package fu.stockspace.stockspace_be.chatbot.entity;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;




public enum KnowledgeCategory {
    POLICY,
    FAQ,
    CANCELLATION,
    INSURANCE,
    RENTAL_PROCESS;

    public static Optional<KnowledgeCategory> fromExternalValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(category -> category.name().equals(normalized))
                .findFirst();
    }
}

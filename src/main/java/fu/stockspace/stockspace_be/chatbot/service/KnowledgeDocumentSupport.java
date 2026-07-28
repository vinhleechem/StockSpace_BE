package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Canonical serialization and hashing rules shared by indexing and retrieval.
 */
public final class KnowledgeDocumentSupport {

    private KnowledgeDocumentSupport() {
    }

    public static String embeddingText(SystemKnowledge knowledge) {
        return embeddingText(knowledge.getTitle(), knowledge.getContent());
    }

    public static String embeddingText(String title, String content) {
        return safeTrim(title) + "\n" + safeTrim(content);
    }

    public static String contentHash(SystemKnowledge knowledge) {
        return contentHash(knowledge.getTitle(), knowledge.getContent());
    }

    public static String contentHash(String title, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(embeddingText(title, content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public static List<Float> parseVector(String serializedVector) {
        if (serializedVector == null) {
            return List.of();
        }

        String value = serializedVector.trim();
        if (value.length() < 2 || value.charAt(0) != '[' || value.charAt(value.length() - 1) != ']') {
            return List.of();
        }

        String content = value.substring(1, value.length() - 1).trim();
        if (content.isEmpty()) {
            return List.of();
        }

        try {
            String[] parts = content.split(",");
            List<Float> vector = new ArrayList<>(parts.length);
            for (String part : parts) {
                float number = Float.parseFloat(part.trim());
                if (!Float.isFinite(number)) {
                    return List.of();
                }
                vector.add(number);
            }
            return List.copyOf(vector);
        } catch (NumberFormatException exception) {
            return List.of();
        }
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

package fu.stockspace.stockspace_be.wms.dataexchange.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class CanonicalContentHashService {

    private final ObjectMapper objectMapper;

    public CanonicalContentHashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Hashes normalized rows and scope, never the original XLSX bytes. Map
     * keys and numeric scales are normalized so equivalent edits have one hash.
     */
    public String hash(WmsImportType importType, Map<String, ?> scope, List<WmsImportRowInput> rows) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("import_type", importType.name());
        root.set("scope", canonicalize(objectMapper.valueToTree(scope)));
        ArrayNode rowArray = root.putArray("rows");
        rows.stream()
                .sorted(Comparator.comparing(WmsImportRowInput::sheetName, Comparator.nullsFirst(String::compareTo))
                        .thenComparingInt(WmsImportRowInput::rowNumber)
                        .thenComparing(WmsImportRowInput::groupKey, Comparator.nullsFirst(String::compareTo)))
                .forEach(row -> {
                    ObjectNode node = JsonNodeFactory.instance.objectNode();
                    node.put("sheet_name", row.sheetName());
                    node.put("row_number", row.rowNumber());
                    if (row.groupKey() == null) {
                        node.putNull("group_key");
                    } else {
                        node.put("group_key", row.groupKey());
                    }
                    node.set("payload", canonicalize(objectMapper.valueToTree(row.normalizedPayload())));
                    rowArray.add(node);
                });
        try {
            return sha256(objectMapper.writeValueAsString(root));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize canonical import content", ex);
        }
    }

    public String sha256(byte[] bytes) {
        try {
            return toHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String toHex(byte[] digest) {
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte item : digest) {
            hex.append(String.format("%02x", item & 0xff));
        }
        return hex.toString();
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), canonicalize(entry.getValue())));
            sorted.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        if (node.isNumber()) {
            try {
                return JsonNodeFactory.withExactBigDecimals(true)
                        .numberNode(new BigDecimal(node.asText()).stripTrailingZeros());
            } catch (NumberFormatException ignored) {
                return node;
            }
        }
        return node;
    }
}

package fu.stockspace.stockspace_be.chatbot.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last-mile guard against numerical hallucinations.  It is deliberately
 * conservative: if a response contains a material number that is absent from
 * successful tool evidence, the caller receives a safe retry message instead
 * of a plausible-looking but unverifiable answer.
 */
public final class AnswerEvidenceVerifier {

    private static final String INTERNAL_UNVERIFIED_MARKER = "[số liệu chưa xác minh]";
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\d])([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]+)?|[0-9]+)"
                    + "(?:\\s*(ty|tỷ|trieu|triệu|tr|k))?(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern NUMERIC_DATE = Pattern.compile(
            "(?<!\\d)(?:(\\d{4})-(\\d{1,2})-(\\d{1,2})"
                    + "|(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4}))(?!\\d)"
    );

    private AnswerEvidenceVerifier() {
    }

    public static Verification verify(String reply, List<ToolExecutionTrace> traces) {
        return verify(reply, null, traces);
    }

    /** Treats numbers supplied by the user as inputs, not hallucinated facts. */
    public static Verification verify(
            String reply,
            String userMessage,
            List<ToolExecutionTrace> traces
    ) {
        if (reply == null || reply.isBlank()) {
            return Verification.ok();
        }
        Set<String> evidenceNumbers = new LinkedHashSet<>();
        Set<LocalDate> evidenceDates = new LinkedHashSet<>();
        addNumbers(evidenceNumbers, userMessage);
        addDerivedNumbers(evidenceNumbers, userMessage);
        addDates(evidenceDates, userMessage);
        if (traces == null) {
            traces = List.of();
        }
        for (ToolExecutionTrace trace : traces) {
            if (trace == null || !trace.successful()) {
                continue;
            }
            Matcher matcher = NUMBER.matcher(trace.result());
            while (matcher.find()) {
                String canonical = canonicalNumber(matcher.group(1), matcher.group(2));
                if (canonical != null) {
                    evidenceNumbers.add(canonical);
                }
            }
            addDates(evidenceDates, trace.result());
        }

        List<DateMention> replyDates = findDates(reply);
        for (DateMention date : replyDates) {
            if (date.value() == null || !evidenceDates.contains(date.value())) {
                return new Verification(false, reply.substring(date.start(), date.end()));
            }
        }
        Matcher replyMatcher = NUMBER.matcher(reply);
        while (replyMatcher.find()) {
            String raw = replyMatcher.group(1);
            if (isInsideDate(replyMatcher.start(), replyMatcher.end(), replyDates)
                    || isListMarker(reply, replyMatcher.start(), replyMatcher.end())
                    || isLikelyYear(raw)) {
                continue;
            }
            String canonical = canonicalNumber(raw, replyMatcher.group(2));
            if (canonical != null && !evidenceNumbers.contains(canonical)) {
                return new Verification(false, raw);
            }
        }
        return Verification.ok();
    }

    public static String guard(String reply, List<ToolExecutionTrace> traces) {
        return guard(reply, null, traces);
    }

    /** Hard safety fallback retained for callers that require all-or-nothing behavior. */
    public static String guard(
            String reply,
            String userMessage,
            List<ToolExecutionTrace> traces
    ) {
        Verification verification = verify(reply, userMessage, traces);
        if (verification.valid()) {
            return reply;
        }
        return "Tôi chưa thể xác minh một hoặc nhiều con số trong câu trả lời từ dữ liệu hệ thống, "
                + "nên không muốn trả lời sai. Bạn vui lòng thử lại sau.";
    }

    /**
     * Preserves grounded sentences and neutralizes only sentences containing
     * unsupported material numbers. This is the normal chatbot path.
     */
    public static String sanitize(
            String reply,
            String userMessage,
            List<ToolExecutionTrace> traces
    ) {
        Verification verification = verify(reply, userMessage, traces);
        if (reply == null || reply.isBlank()) {
            return reply;
        }
        if (verification.valid() && !reply.contains(INTERNAL_UNVERIFIED_MARKER)) {
            return reply;
        }

        List<String> safeSegments = new ArrayList<>();
        for (String segment : reply.split("(?<=[.!?])\\s+|\\R+")) {
            String trimmed = segment.trim();
            if (!trimmed.isBlank()
                    && !trimmed.contains(INTERNAL_UNVERIFIED_MARKER)
                    && verify(trimmed, userMessage, traces).valid()) {
                safeSegments.add(trimmed);
            }
        }
        if (safeSegments.isEmpty()) {
            return "Tôi đã tra cứu nhưng chưa thể hiển thị chính xác các số liệu trong kết quả. "
                    + "Bạn vui lòng thử lại sau hoặc kiểm tra trực tiếp trên màn hình nghiệp vụ tương ứng.";
        }
        return String.join(" ", safeSegments)
                + " Một số chi tiết số liệu chưa đối chiếu được với dữ liệu hệ thống nên chưa được hiển thị.";
    }

    private static void addNumbers(Set<String> target, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String canonical = canonicalNumber(matcher.group(1), matcher.group(2));
            if (canonical != null) {
                target.add(canonical);
            }
        }
    }

    private static void addDates(Set<LocalDate> target, String text) {
        for (DateMention date : findDates(text)) {
            if (date.value() != null) {
                target.add(date.value());
            }
        }
    }

    private static List<DateMention> findDates(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<DateMention> dates = new ArrayList<>();
        Matcher matcher = NUMERIC_DATE.matcher(text);
        while (matcher.find()) {
            try {
                boolean iso = matcher.group(1) != null;
                int year = Integer.parseInt(matcher.group(iso ? 1 : 6));
                int month = Integer.parseInt(matcher.group(iso ? 2 : 5));
                int day = Integer.parseInt(matcher.group(iso ? 3 : 4));
                dates.add(new DateMention(
                        matcher.start(), matcher.end(), LocalDate.of(year, month, day)));
            } catch (DateTimeException | NumberFormatException exception) {
                dates.add(new DateMention(matcher.start(), matcher.end(), null));
            }
        }
        return dates;
    }

    private static boolean isInsideDate(int start, int end, List<DateMention> dates) {
        return dates.stream().anyMatch(date -> start >= date.start() && end <= date.end());
    }

    /**
     * Allows the verifier to recognize a result that is transparently derived
     * from user-provided inputs (for example, "10 m2 x 100.000/m2"), without
     * accepting arbitrary model-generated numbers. Only an explicit operator
     * between adjacent numeric inputs is considered evidence.
     */
    private static void addDerivedNumbers(Set<String> target, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        List<NumericMention> mentions = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String canonical = canonicalNumber(matcher.group(1), matcher.group(2));
            if (canonical == null) {
                continue;
            }
            try {
                mentions.add(new NumericMention(
                        matcher.start(), matcher.end(), new BigDecimal(canonical)));
            } catch (NumberFormatException ignored) {
                // The regular expression and canonicalizer already reject
                // malformed values; keep this helper fail-safe regardless.
            }
        }
        for (int index = 0; index + 1 < mentions.size(); index++) {
            NumericMention left = mentions.get(index);
            NumericMention right = mentions.get(index + 1);
            String between = normalizeOperators(text.substring(left.end(), right.start()));
            BigDecimal derived = null;
            if (containsOperator(between, "multiply")
                    || (between.contains("gia") && (between.contains("m2")
                    || between.contains("met vuong")))) {
                derived = left.value().multiply(right.value());
            } else if (containsOperator(between, "add")) {
                derived = left.value().add(right.value());
            } else if (containsOperator(between, "subtract")) {
                derived = left.value().subtract(right.value());
            } else if (containsOperator(between, "divide")
                    && right.value().compareTo(BigDecimal.ZERO) != 0) {
                derived = left.value().divide(right.value(), 8, java.math.RoundingMode.HALF_UP);
            }
            if (derived != null && derived.signum() >= 0) {
                target.add(derived.stripTrailingZeros().toPlainString());
            }
        }
    }

    private static String normalizeOperators(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replace('²', '2')
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean containsOperator(String between, String operator) {
        return switch (operator) {
            case "multiply" -> between.matches("(?s).*([×*]|\\bx\\b|\\bnhan\\b|\\bnhan voi\\b).*");
            case "add" -> between.matches("(?s).*([+] |\\bcong\\b|\\bcong voi\\b).*")
                    || between.contains("+");
            case "subtract" -> between.matches("(?s).*\\s-\\s.*|.*\\btru\\b.*");
            case "divide" -> between.matches("(?s).*([/] |\\bchia\\b|\\btren\\b).*")
                    || between.contains("/");
            default -> false;
        };
    }

    private static boolean isListMarker(String text, int start, int end) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        String prefix = text.substring(lineStart, start).trim();
        return prefix.isEmpty() && end - start <= 2;
    }

    private static boolean isLikelyYear(String raw) {
        try {
            int year = Integer.parseInt(raw.replace(",", "").replace(".", ""));
            return year >= 1900 && year <= 2100;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static String canonicalNumber(String raw, String unit) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String normalized = raw.trim();
            int lastDot = normalized.lastIndexOf('.');
            int lastComma = normalized.lastIndexOf(',');
            if (lastDot >= 0 && lastComma >= 0) {
                char decimalSeparator = lastDot > lastComma ? '.' : ',';
                char groupingSeparator = decimalSeparator == '.' ? ',' : '.';
                normalized = normalized.replace(String.valueOf(groupingSeparator), "")
                        .replace(decimalSeparator, '.');
            } else if (lastDot >= 0 || lastComma >= 0) {
                char separator = lastDot >= 0 ? '.' : ',';
                int digitsAfter = normalized.length() - normalized.lastIndexOf(separator) - 1;
                normalized = digitsAfter == 3
                        ? normalized.replace(String.valueOf(separator), "")
                        : normalized.replace(separator, '.');
            }
            BigDecimal value = new BigDecimal(normalized);
            if (unit != null) {
                value = value.multiply(switch (unit.toLowerCase(java.util.Locale.ROOT)) {
                    case "ty", "tỷ" -> BigDecimal.valueOf(1_000_000_000L);
                    case "tr", "trieu", "triệu" -> BigDecimal.valueOf(1_000_000L);
                    case "k" -> BigDecimal.valueOf(1_000L);
                    default -> BigDecimal.ONE;
                });
            }
            return value.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public record Verification(boolean valid, String unsupportedNumber) {
        public static Verification ok() {
            return new Verification(true, null);
        }
    }

    private record NumericMention(int start, int end, BigDecimal value) {
    }

    private record DateMention(int start, int end, LocalDate value) {
    }
}

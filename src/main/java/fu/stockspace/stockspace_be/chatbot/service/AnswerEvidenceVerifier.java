package fu.stockspace.stockspace_be.chatbot.service;

import java.math.BigDecimal;
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

    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\d])([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]+)?|[0-9]+)"
                    + "(?:\\s*(ty|tỷ|trieu|triệu|tr|k))?(?![\\p{L}\\d])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private AnswerEvidenceVerifier() {
    }

    public static Verification verify(String reply, List<ToolExecutionTrace> traces) {
        if (reply == null || reply.isBlank() || traces == null || traces.isEmpty()) {
            return Verification.ok();
        }
        Set<String> evidenceNumbers = new LinkedHashSet<>();
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
        }

        Matcher replyMatcher = NUMBER.matcher(reply);
        while (replyMatcher.find()) {
            String raw = replyMatcher.group(1);
            if (isListMarker(reply, replyMatcher.start(), replyMatcher.end())
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
        Verification verification = verify(reply, traces);
        if (verification.valid()) {
            return reply;
        }
        return "Tôi chưa thể xác minh một hoặc nhiều con số trong câu trả lời từ dữ liệu hệ thống, "
                + "nên không muốn trả lời sai. Bạn vui lòng thử lại sau.";
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
}

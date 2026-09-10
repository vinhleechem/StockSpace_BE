package fu.stockspace.stockspace_be.chatbot.service;

import java.text.Normalizer;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Domain vocabulary used when a user describes the same warehouse concept in
 * different words.  It is intentionally deterministic: embeddings can rank
 * candidates, but these aliases make the fallback path useful when the
 * embedding provider is unavailable or a user makes a small typing mistake.
 */
public final class SemanticQueryExpansion {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final List<AliasGroup> GROUPS = List.of(
            group("kho lanh", "kho lanh", "kho mat", "kho dong", "bao quan thuc pham",
                    "chuoi lanh", "nhiet do", "2 8"),
            group("kho mat", "kho mat", "kho lanh", "bao quan thuc pham", "nhiet do"),
            group("dong lanh", "dong lanh", "kho dong", "kho lanh", "nhiet do"),
            group("bao quan", "bao quan", "bao quan thuc pham", "kho lanh", "kho mat"),
            group("nong san", "nong san", "rau cu", "trai cay", "thuc pham tuoi",
                    "hai san", "luong thuc"),
            group("thuc pham", "thuc pham", "thuc pham tuoi", "nong san", "rau cu",
                    "trai cay", "hai san", "bao quan"),
            group("rau cu", "rau cu", "nong san", "thuc pham tuoi", "bao quan"),
            group("trai cay", "trai cay", "nong san", "thuc pham tuoi", "bao quan"),
            group("hai san", "hai san", "thuc pham tuoi", "nong san", "bao quan", "kho lanh"),
            group("dien tu", "dien tu", "linh kien dien tu", "linh kien", "thiet bi dien",
                    "hang cong nghe", "may moc"),
            group("linh kien", "linh kien", "linh kien dien tu", "dien tu", "thiet bi dien"),
            group("hang cong nghe", "hang cong nghe", "dien tu", "linh kien", "may moc"),
            group("thiet bi dien", "thiet bi dien", "dien tu", "linh kien", "hang cong nghe"),
            group("vat lieu xay dung", "vat lieu xay dung", "nguyen vat lieu", "sat thep",
                    "xi mang", "gach", "hang nang"),
            group("nguyen vat lieu", "nguyen vat lieu", "vat lieu xay dung", "sat thep",
                    "xi mang", "gach"),
            group("pallet", "pallet", "ke hang", "ke pallet", "gia ke", "kho hang"),
            group("ke hang", "ke hang", "pallet", "ke pallet", "gia ke", "kho hang"),
            group("thuong mai dien tu", "thuong mai dien tu", "e commerce", "dong goi",
                    "xu ly don", "fulfillment", "hang tieu dung"),
            group("fulfillment", "fulfillment", "thuong mai dien tu", "dong goi", "xu ly don"),
            group("dong goi", "dong goi", "xu ly don", "fulfillment", "thuong mai dien tu"),
            group("dat coc", "dat coc", "tien coc", "ky quy", "tam ung"),
            group("tam ung", "tam ung", "dat coc", "tien coc", "ky quy"),
            group("bao hiem", "bao hiem", "den bu", "boi thuong", "mat mat", "hu hong"),
            group("den bu", "den bu", "bao hiem", "boi thuong", "mat mat", "hu hong"),
            group("hoan coc", "hoan coc", "dat coc", "tien coc", "boi thuong"),
            group("huy hop dong", "huy hop dong", "cham dut", "thanh ly hop dong", "hoan coc"),
            group("cham dut", "cham dut", "huy hop dong", "thanh ly hop dong", "hoan coc"),
            group("gia han", "gia han", "tai ky", "noi tiep hop dong", "hop dong moi"),
            group("tai ky", "tai ky", "gia han", "noi tiep hop dong", "hop dong moi"),
            group("kiem dinh", "kiem dinh", "tham dinh", "xem xet kho", "xac minh kho"),
            group("tham dinh", "tham dinh", "kiem dinh", "xem xet kho", "xac minh kho"),
            group("xac nhan hop dong", "xac nhan hop dong", "chap nhan hop dong", "ky hop dong"),
            group("ky hop dong", "ky hop dong", "xac nhan hop dong", "chap nhan hop dong")
    );

    private SemanticQueryExpansion() {
    }

    /** Returns matching aliases, including the trigger itself, in stable order. */
    public static Set<String> expand(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (AliasGroup group : GROUPS) {
            if (matches(normalized, group.trigger())) {
                result.addAll(group.aliases());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .replace('²', '2')
                .replace('³', '3');
        return NON_WORD.matcher(withoutDiacritics.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static boolean matches(String normalized, String trigger) {
        if (normalized.contains(trigger)) {
            return true;
        }
        String[] queryTokens = normalized.split("\\s+");
        String[] triggerTokens = trigger.split("\\s+");
        if (triggerTokens.length > 2) {
            return false;
        }
        for (String triggerToken : triggerTokens) {
            boolean found = false;
            for (String queryToken : queryTokens) {
                if (queryToken.equals(triggerToken)
                        || (queryToken.length() >= 3
                        && oneEditAway(queryToken, triggerToken))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean oneEditAway(String left, String right) {
        if (levenshteinDistance(left, right, 1) <= 1) {
            return true;
        }
        if (left.length() != right.length()) {
            return false;
        }
        for (int index = 0; index + 1 < left.length(); index++) {
            if (left.charAt(index) == right.charAt(index)) {
                continue;
            }
            if (left.charAt(index) != right.charAt(index + 1)
                    || left.charAt(index + 1) != right.charAt(index)) {
                return false;
            }
            String swapped = left.substring(0, index)
                    + right.charAt(index)
                    + right.charAt(index + 1)
                    + left.substring(index + 2);
            return swapped.equals(right);
        }
        return false;
    }

    private static int levenshteinDistance(String left, String right, int cutoff) {
        if (Math.abs(left.length() - right.length()) > cutoff) {
            return cutoff + 1;
        }
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitution = previous[rightIndex - 1]
                        + (left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1);
                current[rightIndex] = Math.min(Math.min(
                        previous[rightIndex] + 1,
                        current[rightIndex - 1] + 1), substitution);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static AliasGroup group(String trigger, String... aliases) {
        return new AliasGroup(trigger, List.of(aliases));
    }

    private record AliasGroup(String trigger, List<String> aliases) {
    }
}

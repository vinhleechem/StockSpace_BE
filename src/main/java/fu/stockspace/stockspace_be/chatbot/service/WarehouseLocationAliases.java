package fu.stockspace.stockspace_be.chatbot.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts common conversational city aliases into the location labels stored
 * on warehouse records.  The chatbot can receive a location from either the
 * deterministic planner or the model tool call, so this normalization is
 * deliberately shared by both paths.
 */
public final class WarehouseLocationAliases {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("hcm", "Hồ Chí Minh"),
            Map.entry("tp hcm", "Hồ Chí Minh"),
            Map.entry("tphcm", "Hồ Chí Minh"),
            Map.entry("ho chi minh", "Hồ Chí Minh"),
            Map.entry("tp ho chi minh", "Hồ Chí Minh"),
            Map.entry("thanh pho ho chi minh", "Hồ Chí Minh"),
            Map.entry("sai gon", "Hồ Chí Minh"),
            Map.entry("saigon", "Hồ Chí Minh"),
            Map.entry("hn", "Hà Nội"),
            Map.entry("hanoi", "Hà Nội"),
            Map.entry("ha noi", "Hà Nội"),
            Map.entry("tp ha noi", "Hà Nội"),
            Map.entry("thanh pho ha noi", "Hà Nội"),
            Map.entry("da nang", "Đà Nẵng"),
            Map.entry("tp da nang", "Đà Nẵng"),
            Map.entry("thanh pho da nang", "Đà Nẵng"),
            Map.entry("hai phong", "Hải Phòng"),
            Map.entry("tp hai phong", "Hải Phòng"),
            Map.entry("thanh pho hai phong", "Hải Phòng"),
            Map.entry("can tho", "Cần Thơ"),
            Map.entry("tp can tho", "Cần Thơ"),
            Map.entry("thanh pho can tho", "Cần Thơ"),
            Map.entry("binh duong", "Bình Dương"),
            Map.entry("dong nai", "Đồng Nai"),
            Map.entry("ba ria vung tau", "Bà Rịa - Vũng Tàu")
    );

    private WarehouseLocationAliases() {
    }

    public static String canonicalProvince(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        return PROVINCE_ALIASES.getOrDefault(normalize(value), value.trim());
    }

    private static String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return NON_WORD.matcher(withoutDiacritics.toLowerCase(Locale.ROOT)).replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}

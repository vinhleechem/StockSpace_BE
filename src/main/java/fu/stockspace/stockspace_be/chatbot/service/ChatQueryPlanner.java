package fu.stockspace.stockspace_be.chatbot.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the user's natural-language request into a small, model-readable
 * plan.  The plan is advisory for the LLM and authoritative for deterministic
 * preloading, so tool arguments remain the final source of truth.
 */
public final class ChatQueryPlanner {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\d])([0-9][0-9.,]*)(?:\\s*)(ty|tỷ|trieu|triệu|tr|k)?(?![\\p{L}])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CAPACITY = Pattern.compile(
            "(?<![\\p{L}\\d])([0-9][0-9.,]*)\\s*(?:m2|met vuong)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LOCATION = Pattern.compile(
            "(?iu)(?:\\b(?:ở|tại|khu vực|gần)\\s+)([^,;!?]+?)(?=\\s+(?:giá|theo|từ|dưới|trên|có|với|và|tối thiểu|ít nhất|phù hợp|còn|đang)|[,;!?]|$)",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Set<String> SEARCH_MARKERS = Set.of(
            "tim", "kiem", "danh sach", "loc", "goi y", "kho nao", "co kho",
            "o dau", "tai dau", "quan nao", "tinh nao", "con trong", "con cho thue",
            "gia thue kho", "gia re", "gan"
    );
    private static final Set<String> CONTEXTUAL_FOLLOW_UP_MARKERS = Set.of(
            "con kho", "them kho", "kho khac", "re hon", "dat hon", "phu hop hon",
            "o do", "tai do", "khu vuc khac", "loc lai", "theo bo loc", "tiep theo",
            "so sanh", "nhu vay", "cai nao", "loai nao", "con khong", "co khong",
            "cai dau", "cai thu", "dau tien", "thu hai", "doi sang", "chuyen sang",
            "tim them", "tim lai", "them nua"
    );
    private static final Set<String> RESET_SEARCH_MARKERS = Set.of(
            "xoa bo loc", "xoa loc", "bo bo loc", "bo het loc", "tim lai tu dau", "tim moi", "clear filters"
    );
    private static final Set<String> OPERATION_MARKERS = Set.of(
            "ton kho", "sku", "san pham", "phieu nhap", "phieu xuat", "kiem ke",
            "chuyen kho", "xep hang", "lay hang", "nhap hang", "suc chua van hanh",
            "kho cua toi", "kho cua minh", "kho toi", "kho minh", "kho dang xem", "kho dang mo", "my warehouse"
    );
    private static final Set<String> WARD_PREFIXES = Set.of(
            "phuong ", "xa ", "thi tran ", "ward ", "commune "
    );
    private static final Set<String> PROVINCE_NAMES = Set.of(
            "ha noi", "hai phong", "da nang", "can tho", "ho chi minh",
            "an giang", "ba ria vung tau", "bac lieu", "bac kan", "bac ninh",
            "ben tre", "binh dinh", "binh duong", "binh phuoc", "binh thuan",
            "ca mau", "cao bang", "dak lak", "dak nong", "dien bien",
            "dong nai", "dong thap", "gia lai", "ha giang", "ha nam",
            "ha tinh", "hai duong", "hau giang", "hoa binh", "hung yen",
            "khanh hoa", "kien giang", "kon tum", "lai chau", "lam dong",
            "lang son", "lao cai", "long an", "nam dinh", "nghe an",
            "ninh binh", "ninh thuan", "phu tho", "phu yen", "quang binh",
            "quang nam", "quang ngai", "quang ninh", "quang tri", "soc trang",
            "son la", "tay ninh", "thai binh", "thai nguyen", "thanh hoa",
            "thua thien hue", "tien giang", "tra vinh", "tuyen quang",
            "vinh long", "vinh phuc", "yen bai"
    );

    private ChatQueryPlanner() {
    }

    public static Plan plan(String message) {
        return plan(message, Map.of());
    }

    /** Plans a turn while optionally carrying forward the previous warehouse-search filters. */
    public static Plan plan(String message, Map<String, Object> previousWarehouseSearch) {
        Map<String, Object> previous = previousWarehouseSearch == null
                ? Map.of()
                : previousWarehouseSearch;
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return Plan.none();
        }

        RentalIntentClassifier.Intent rentalIntent = RentalIntentClassifier.classify(message);
        if (rentalIntent.route() != RentalIntentClassifier.Route.NONE) {
            return new Plan(
                    switch (rentalIntent.route()) {
                        case CURRENT_SYSTEM_RULES -> Intent.CURRENT_RULES;
                        case SYSTEM_INFO -> Intent.SYSTEM_INFO;
                        case WAREHOUSE_TYPES -> Intent.WAREHOUSE_TYPES;
                        case SERVICE_PACKAGES -> Intent.SERVICE_PACKAGES;
                        case MY_ACTIVE_SUBSCRIPTION -> Intent.MY_ACTIVE_SUBSCRIPTION;
                        case MY_CONTRACTS -> Intent.MY_CONTRACTS;
                        case SYSTEM_POLICY -> Intent.RENTAL_POLICY;
                        case NONE -> Intent.NONE;
                    },
                    Map.of(),
                    true
            );
        }

        if (isWarehouseSearch(normalized)) {
            Plan current = new Plan(Intent.WAREHOUSE_SEARCH,
                    extractWarehouseFilters(message, normalized), true);
            return mergeWarehouseFollowUp(message, normalized, current, previous);
        }
        if (!previous.isEmpty()
                && isWarehouseFollowUp(normalized)) {
            return continueWarehouseSearch(message, normalized, previous);
        }
        return Plan.none();
    }

    private static Plan mergeWarehouseFollowUp(
            String original,
            String normalized,
            Plan current,
            Map<String, Object> previous
    ) {
        if (previous == null || previous.isEmpty()
                || (!isWarehouseFollowUp(normalized) && !resetsWarehouseFilters(normalized))) {
            return current;
        }
        boolean reset = resetsWarehouseFilters(normalized);
        Map<String, Object> merged = reset
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(previous);
        merged.putAll(current.filters());
        String location = extractLocation(original);
        boolean explicitLocation = location != null
                && !Set.of("do", "day", "kia", "nay").contains(normalize(location));
        String previousSemantic = reset ? "" : stringValue(
                previous.get("semanticQuery"),
                stringValue(previous.get("keyword"), ""));
        String currentSemantic = stringValue(current.filters().get("semanticQuery"), original);
        if (!previousSemantic.isBlank()) {
            // A follow-up such as "ở Bình Dương" changes the location but
            // keeps the original business need (for example "kho lạnh").
            // Keep both signals for the vector ranker instead of dropping the
            // previous requirement when a new location is supplied.
            merged.put("semanticQuery", combineSemanticQueries(currentSemantic, previousSemantic));
        }
        if (!reset && !explicitLocation && previous.get("keyword") != null) {
            merged.put("keyword", previous.get("keyword"));
            merged.put("semanticQuery", combineSemanticQueries(
                    currentSemantic,
                    previousSemantic));
        } else if (explicitLocation) {
            if (!current.filters().containsKey("province")) {
                merged.remove("province");
            }
            if (!current.filters().containsKey("district")) {
                merged.remove("district");
            }
        }
        merged.putIfAbsent("pageSize", 5);
        return new Plan(Intent.WAREHOUSE_SEARCH, merged, true);
    }

    private static Plan continueWarehouseSearch(
            String original,
            String normalized,
            Map<String, Object> previous
    ) {
        if (resetsWarehouseFilters(normalized)) {
            Map<String, Object> resetFilters = extractWarehouseFilters(original, normalized);
            if (extractLocation(original) == null) {
                resetFilters.remove("keyword");
                resetFilters.remove("semanticQuery");
            }
            return new Plan(Intent.WAREHOUSE_SEARCH, resetFilters, true);
        }
        Map<String, Object> filters = new LinkedHashMap<>(previous);
        String previousKeyword = stringValue(previous.get("keyword"), null);
        if (previousKeyword != null) {
            filters.put("keyword", previousKeyword);
        }
        filters.put("semanticQuery", combineSemanticQueries(
                original,
                stringValue(previous.get("semanticQuery"), previousKeyword == null ? "" : previousKeyword)));
        String location = extractLocation(original);
        if (location != null
                && !Set.of("do", "day", "kia", "nay").contains(normalize(location))) {
            String normalizedLocation = normalize(location);
            String canonicalLocation = WarehouseLocationAliases.canonicalProvince(location);
            filters.put("keyword", canonicalLocation);
            filters.remove("province");
            filters.remove("district");
            if (normalizedLocation.startsWith("quan ")
                    || normalizedLocation.startsWith("huyen ")
                    || normalizedLocation.startsWith("thi xa ")) {
                filters.put("district", location);
            } else if (!WARD_PREFIXES.stream().anyMatch(normalizedLocation::startsWith)
                    && (normalizedLocation.startsWith("tinh ")
                    || normalizedLocation.startsWith("thanh pho ")
                    || normalizedLocation.startsWith("tp ")
                    || PROVINCE_NAMES.contains(normalizedLocation))) {
                filters.put("province", canonicalLocation);
            }
        }
        filters.putIfAbsent("pageSize", 5);
        applyRelativeSort(normalized, filters);
        return new Plan(Intent.WAREHOUSE_SEARCH, filters, true);
    }

    private static boolean isWarehouseFollowUp(String normalized) {
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        return containsAny(normalized, CONTEXTUAL_FOLLOW_UP_MARKERS)
                || normalized.matches("^(con|them|khac|re hon|dat hon|o do|tai do)\\b.*")
                || isLocationOnlyFollowUp(normalized)
                || resetsWarehouseFilters(normalized);
    }

    private static boolean resetsWarehouseFilters(String normalized) {
        return containsAny(normalized, RESET_SEARCH_MARKERS);
    }

    private static boolean isLocationOnlyFollowUp(String normalized) {
        return normalized.matches(
                "^(o|tai|khu vuc|gan)\\s+(?!dau(?:\\s|$)).{2,}$");
    }

    private static String combineSemanticQueries(String current, String previous) {
        String left = current == null ? "" : current.strip();
        String right = previous == null ? "" : previous.strip();
        if (left.isBlank()) {
            return right;
        }
        if (right.isBlank() || left.equalsIgnoreCase(right)) {
            return left;
        }
        String combined = left + " | " + right;
        return combined.length() <= 1_000 ? combined : combined.substring(0, 1_000);
    }

    private static String stringValue(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    /**
     * Decomposes compound rental questions into deterministic retrieval units.
     * The old planner selected one route for the entire sentence, causing the
     * second half of questions such as "gia hạn và bảo hiểm" to be ignored.
     */
    public static List<SubQuery> decompose(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : message.split("(?iu)\\s+(?:và|va|and)\\s+|[;?]+")) {
            if (!part.isBlank()) {
                parts.add(part.trim());
            }
        }
        if (parts.size() <= 1) {
            parts = List.of(message.trim());
        }

        List<SubQuery> result = new ArrayList<>();
        for (String part : parts) {
            RentalIntentClassifier.Intent intent = RentalIntentClassifier.classify(part);
            if (intent.route() == RentalIntentClassifier.Route.NONE) {
                continue;
            }
            result.add(new SubQuery(
                    part,
                    intent,
                    rentalArguments(intent, part)
            ));
        }
        if (result.isEmpty()) {
            RentalIntentClassifier.Intent intent = RentalIntentClassifier.classify(message);
            if (intent.route() != RentalIntentClassifier.Route.NONE) {
                result.add(new SubQuery(message.trim(), intent, rentalArguments(intent, message)));
            }
        }
        // Keep one retrieval for a compound question when every part maps to
        // the same policy bucket.  This preserves the full user wording (and
        // therefore all lexical context) while still splitting genuinely
        // different topics such as renewal + insurance.
        if (result.size() > 1 && result.stream().map(SubQuery::intent)
                .map(RentalIntentClassifier.Intent::requiredTool)
                .distinct().count() == 1
                && result.stream().map(SubQuery::intent)
                .map(RentalIntentClassifier.Intent::category)
                .distinct().count() == 1) {
            RentalIntentClassifier.Intent intent = result.get(0).intent();
            return List.of(new SubQuery(message.trim(), intent, rentalArguments(intent, message)));
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> rentalArguments(
            RentalIntentClassifier.Intent intent,
            String query
    ) {
        if (!"searchSystemPolicy".equals(intent.requiredTool())) {
            return Map.of();
        }
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", query);
        args.put("topK", 4);
        if (intent.category() != null) {
            args.put("category", intent.category());
        }
        return Map.copyOf(args);
    }

    private static boolean isWarehouseSearch(String normalized) {
        boolean locationFollowUp = normalized.matches(
                "^(o|tai|khu vuc|gan)\\s+(?!dau(?:\\s|$)).{2,}$");
        if ((!normalized.contains("kho") && !locationFollowUp)
                || containsAny(normalized, OPERATION_MARKERS)
                || normalized.contains("dien tich") || normalized.contains("kich thuoc")
                || normalized.contains("bao nhieu m2")) {
            return false;
        }
        return containsAny(normalized, SEARCH_MARKERS)
                || normalized.matches(".*\\bkho\\s+[^ ]+.*")
                || normalized.contains("cho thue")
                || normalized.contains("thue kho")
                || locationFollowUp;
    }

    private static Map<String, Object> extractWarehouseFilters(String original, String normalized) {
        Map<String, Object> filters = new LinkedHashMap<>();
        String originalText = original == null ? "" : original.strip();
        filters.put("keyword", originalText);
        filters.put("pageSize", 5);

        String location = extractLocation(original);
        if (location != null) {
            String normalizedLocation = normalize(location);
            String canonicalLocation = WarehouseLocationAliases.canonicalProvince(location);
            // Keep the location as the lexical anchor and the full sentence as
            // the semantic query. This prevents SQL LIKE from receiving the
            // entire natural-language question while preserving intent for
            // vector ranking.
            filters.put("keyword", canonicalLocation);
            filters.put("semanticQuery", originalText);
            if (normalizedLocation.startsWith("quan ")
                    || normalizedLocation.startsWith("huyen ")
                    || normalizedLocation.startsWith("thi xa ")) {
                filters.put("district", canonicalLocation);
            } else if (!WARD_PREFIXES.stream().anyMatch(normalizedLocation::startsWith)
                    && (normalizedLocation.startsWith("tinh ")
                    || normalizedLocation.startsWith("thanh pho ")
                    || normalizedLocation.startsWith("tp ")
                    || PROVINCE_NAMES.contains(normalizedLocation))) {
                filters.put("province", canonicalLocation);
            }
        }

        if (normalized.contains("theo m2") || normalized.contains("moi m2")
                || normalized.contains("m2 moi thang")) {
            filters.put("pricingType", "PER_SQUARE_METER_MONTHLY");
        } else if (normalized.contains("thoa thuan")) {
            filters.put("pricingType", "NEGOTIATED");
        } else if (normalized.contains("co dinh") || normalized.contains("theo thang")) {
            filters.put("pricingType", "FIXED_MONTHLY");
        }

        Matcher capacityMatcher = CAPACITY.matcher(normalized);
        if (capacityMatcher.find()) {
            BigDecimal capacity = parseNumber(capacityMatcher.group(1), null);
            if (capacity != null) {
                String capacityContext = normalized.substring(
                        Math.max(0, capacityMatcher.start() - 28), capacityMatcher.start());
                if (containsAny(capacityContext, Set.of("it nhat", "toi thieu", "tu ", "tren "))) {
                    filters.put("minCapacity", capacity);
                } else if (containsAny(capacityContext, Set.of("toi da", "duoi ", "khong qua"))) {
                    filters.put("maxCapacity", capacity);
                }
            }
        }

        Matcher numberMatcher = NUMBER.matcher(normalized);
        String firstPriceRaw = null;
        String firstPriceUnit = null;
        String secondPriceRaw = null;
        String secondPriceUnit = null;
        while (numberMatcher.find()) {
            String before = normalized.substring(
                    Math.max(0, numberMatcher.start() - 32), numberMatcher.start());
            boolean priceContext = before.contains("gia")
                    || before.contains("phi")
                    || before.contains("ngan sach")
                    || normalized.contains("re hon")
                    || normalized.contains("dat hon")
                    || normalized.contains("duoi ")
                    || normalized.contains("tren ")
                    || (normalized.contains(" tu ") && normalized.contains(" den ")
                    && (normalized.contains("trieu") || normalized.contains("ty")
                    || normalized.contains(" tr ") || normalized.contains(" k ")));
            if (!priceContext) {
                continue;
            }
            if (firstPriceRaw == null) {
                firstPriceRaw = numberMatcher.group(1);
                firstPriceUnit = numberMatcher.group(2);
            } else if (secondPriceRaw == null) {
                secondPriceRaw = numberMatcher.group(1);
                secondPriceUnit = numberMatcher.group(2);
            }
        }
        // Vietnamese price ranges commonly write the unit only once at the
        // end ("từ 5 đến 15 triệu"). Infer that unit for the first bound.
        String inferredUnit = firstPriceUnit == null ? secondPriceUnit : firstPriceUnit;
        BigDecimal firstPrice = parseNumber(firstPriceRaw, inferredUnit);
        BigDecimal secondPrice = parseNumber(
                secondPriceRaw,
                secondPriceUnit == null ? inferredUnit : secondPriceUnit
        );
        boolean hasRange = normalized.contains(" tu ") && normalized.contains(" den ");
        if (hasRange && firstPrice != null) {
            filters.put("minRentalPrice", firstPrice);
            if (secondPrice != null) {
                filters.put("maxRentalPrice", secondPrice);
            }
        } else if (firstPrice != null) {
            if (containsAny(normalized, Set.of("duoi ", "toi da", "khong qua", "re hon"))) {
                filters.put("maxRentalPrice", firstPrice);
            } else if (containsAny(normalized, Set.of(
                    "tren ", "toi thieu", "it nhat", "dat hon", "gia cao hon"))) {
                filters.put("minRentalPrice", firstPrice);
            }
        }
        if (normalized.contains("da xac minh") || normalized.contains("da duyet")
                || normalized.contains("verified")) {
            filters.put("isVerified", true);
        }
        if (normalized.contains("re nhat") || normalized.contains("thap nhat")) {
            filters.put("sortBy", "PRICE_ASC");
        } else if (normalized.contains("cao nhat") || normalized.contains("dat nhat")) {
            filters.put("sortBy", "PRICE_DESC");
        }
        applyRelativeSort(normalized, filters);
        return Map.copyOf(filters);
    }

    private static void applyRelativeSort(String normalized, Map<String, Object> filters) {
        if (normalized.contains("re hon") || normalized.contains("gia thap hon")) {
            filters.put("sortBy", "PRICE_ASC");
        } else if (normalized.contains("dat hon") || normalized.contains("gia cao hon")) {
            filters.put("sortBy", "PRICE_DESC");
        }
    }

    private static String extractLocation(String original) {
        if (original == null || original.isBlank()) {
            return null;
        }
        Matcher matcher = LOCATION.matcher(original);
        if (!matcher.find()) {
            return null;
        }
        String location = matcher.group(1).strip();
        return location.isBlank() ? null : location;
    }

    private static BigDecimal parseNumber(String raw, String unit) {
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
            if (unit == null) {
                return value;
            }
            return switch (unit.toLowerCase(Locale.ROOT)) {
                case "ty", "tỷ" -> value.multiply(BigDecimal.valueOf(1_000_000_000L));
                case "tr", "trieu", "triệu" -> value.multiply(BigDecimal.valueOf(1_000_000L));
                case "k" -> value.multiply(BigDecimal.valueOf(1_000L));
                default -> value;
            };
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static boolean containsAny(String text, Set<String> values) {
        return values.stream().anyMatch(text::contains);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        return NON_WORD.matcher(DIACRITICS.matcher(decomposed).replaceAll("")
                        .replace('đ', 'd')
                        .replace('Đ', 'D')
                        .replace('²', '2')
                        .replace('³', '3')
                        .toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    public enum Intent {
        NONE,
        WAREHOUSE_SEARCH,
        RENTAL_POLICY,
        CURRENT_RULES,
        SYSTEM_INFO,
        WAREHOUSE_TYPES,
        SERVICE_PACKAGES,
        MY_ACTIVE_SUBSCRIPTION,
        MY_CONTRACTS
    }

    public record SubQuery(
            String query,
            RentalIntentClassifier.Intent intent,
            Map<String, Object> arguments
    ) {
        public SubQuery {
            query = query == null ? "" : query.trim();
            intent = intent == null ? RentalIntentClassifier.Intent.none() : intent;
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }

        public String requiredTool() {
            return intent.requiredTool();
        }
    }

    public record Plan(Intent intent, Map<String, Object> filters, boolean requiresEvidence) {
        public Plan {
            intent = intent == null ? Intent.NONE : intent;
            filters = filters == null ? Map.of() : Map.copyOf(filters);
        }

        public static Plan none() {
            return new Plan(Intent.NONE, Map.of(), false);
        }

        public String requiredTool() {
            return switch (intent) {
                case WAREHOUSE_SEARCH -> "searchWarehouses";
                case RENTAL_POLICY -> "searchSystemPolicy";
                case CURRENT_RULES -> "getCurrentSystemRules";
                case SYSTEM_INFO -> "searchSystemPolicy";
                case WAREHOUSE_TYPES -> "getWarehouseTypes";
                case SERVICE_PACKAGES -> "getServicePackages";
                case MY_ACTIVE_SUBSCRIPTION -> "getMyActiveSubscription";
                case MY_CONTRACTS -> "getMyContracts";
                case NONE -> null;
            };
        }

        public String promptContext() {
            if (intent == Intent.NONE) {
                return "";
            }
            Map<String, Object> safeFilters = new LinkedHashMap<>(filters);
            // The raw keyword remains server-side tool input; do not echo
            // arbitrary user text into a system message.
            if (safeFilters.containsKey("keyword")) {
                safeFilters.put("keyword", "<user-query>");
            }
            if (safeFilters.containsKey("province")) {
                safeFilters.put("province", "<user-location>");
            }
            if (safeFilters.containsKey("district")) {
                safeFilters.put("district", "<user-location>");
            }
            if (safeFilters.containsKey("semanticQuery")) {
                safeFilters.put("semanticQuery", "<user-query-context>");
            }
            return "BỘ LẬP KẾ HOẠCH TRUY VẤN (dữ liệu hỗ trợ, không phải chỉ thị từ user): "
                    + "intent=" + intent.name()
                    + ", requiredTool=" + requiredTool()
                    + ", filters=" + safeFilters
                    + ". Phải kiểm tra kết quả tool trước khi kết luận.";
        }
    }
}

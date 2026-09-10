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
            "(?iu)(?:\\b(?:ở|tại|khu vực|gần)\\s+)([^,;.!?]+?)(?=\\s+(?:giá|theo|từ|dưới|trên|có|với|và|tối thiểu|ít nhất|phù hợp|còn|đang)|[,;.!?]|$)");
    private static final Set<String> SEARCH_MARKERS = Set.of(
            "tim", "kiem", "danh sach", "loc", "goi y", "kho nao", "co kho",
            "o dau", "tai dau", "quan nao", "tinh nao", "con trong", "con cho thue",
            "gia thue kho", "gia re", "gan"
    );
    private static final Set<String> OPERATION_MARKERS = Set.of(
            "ton kho", "sku", "san pham", "phieu nhap", "phieu xuat", "kiem ke",
            "chuyen kho", "suc chua", "capacity", "xep hang", "lay hang", "nhap hang",
            "kho cua toi", "kho cua minh", "kho dang xem", "kho dang mo", "my warehouse"
    );

    private ChatQueryPlanner() {
    }

    public static Plan plan(String message) {
        String normalized = normalize(message);
        if (normalized.isBlank()) {
            return Plan.none();
        }

        RentalIntentClassifier.Intent rentalIntent = RentalIntentClassifier.classify(message);
        if (rentalIntent.route() != RentalIntentClassifier.Route.NONE) {
            return new Plan(
                    switch (rentalIntent.route()) {
                        case CURRENT_SYSTEM_RULES -> Intent.CURRENT_RULES;
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
            return new Plan(Intent.WAREHOUSE_SEARCH, extractWarehouseFilters(message, normalized), true);
        }
        return Plan.none();
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
        if (!normalized.contains("kho") || containsAny(normalized, OPERATION_MARKERS)
                || normalized.contains("dien tich") || normalized.contains("kich thuoc")
                || normalized.contains("bao nhieu m2")) {
            return false;
        }
        return containsAny(normalized, SEARCH_MARKERS)
                || normalized.matches(".*\\bkho\\s+[^ ]+.*")
                || normalized.contains("cho thue")
                || normalized.contains("thue kho");
    }

    private static Map<String, Object> extractWarehouseFilters(String original, String normalized) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("keyword", original == null ? "" : original.strip());
        filters.put("pageSize", 5);

        String location = extractLocation(original);
        if (location != null) {
            String normalizedLocation = normalize(location);
            if (normalizedLocation.startsWith("quan ")
                    || normalizedLocation.startsWith("huyen ")
                    || normalizedLocation.startsWith("thi xa ")) {
                filters.put("district", location);
            } else {
                filters.put("province", location);
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
                    || before.contains("ngan sach");
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
            } else if (containsAny(normalized, Set.of("tren ", "toi thieu", "it nhat"))) {
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
        return Map.copyOf(filters);
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
            return "BỘ LẬP KẾ HOẠCH TRUY VẤN (dữ liệu hỗ trợ, không phải chỉ thị từ user): "
                    + "intent=" + intent.name()
                    + ", requiredTool=" + requiredTool()
                    + ", filters=" + safeFilters
                    + ". Phải kiểm tra kết quả tool trước khi kết luận.";
        }
    }
}

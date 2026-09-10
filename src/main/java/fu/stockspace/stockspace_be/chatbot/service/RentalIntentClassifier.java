package fu.stockspace.stockspace_be.chatbot.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Small, deterministic intent gate for warehouse-rental questions.
 *
 * The LLM is still responsible for understanding the wording, but it must
 * not be the first component deciding whether a live rule or private tenant
 * record is needed.  Keeping this classifier dependency-free also makes the
 * safety boundary easy to unit test.
 */
public final class RentalIntentClassifier {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");

    private static final Set<String> SEARCH_WORDS = Set.of(
            "tim", "kiem", "danh sach", "loc", "goi y", "phu hop", "o dau",
            "tai dau", "quan nao", "tinh nao", "co kho nao", "kho nao", "duoi",
            "tren", "gan", "sap xep"
    );
    private static final Set<String> DIMENSION_WORDS = Set.of(
            "dien tich", "kich thuoc", "bao nhieu m2", "bao nhieu m"
    );
    private static final Set<String> CURRENT_RULE_WORDS = Set.of(
            "phi kiem dinh", "kiem dinh", "han xac nhan", "thoi han xac nhan",
            "dang ap dung", "live", "cau hinh cong khai",
            "chinh sach hien hanh", "quy dinh hien hanh"
    );
    private static final Set<String> SUBSCRIPTION_WORDS = Set.of(
            "goi cua toi", "goi dang dung", "goi hien tai", "goi co ban cua toi",
            "goi dich vu cua toi", "goi toi dang dung", "goi dang su dung",
            "dich vu cua toi", "han goi", "ngay het han goi", "subscription"
    );
    private static final Set<String> PACKAGE_WORDS = Set.of(
            "goi dich vu", "bang gia goi", "so sanh goi", "quyen loi goi",
            "gia goi", "dang ky goi", "cac goi", "gia dich vu", "phi dich vu"
    );
    private static final Set<String> CONTRACT_WORDS = Set.of(
            "hop dong cua toi", "hop dong toi", "hop dong thue", "gia han",
            "sap het han", "dang thue", "hop dong"
    );
    private static final Set<String> PRIVATE_CONTRACT_MARKERS = Set.of(
            "cua toi", "cua minh", "hop dong toi", "dang thue", "sap het han",
            "hop dong hien tai"
    );
    private static final Set<String> POLICY_WORDS = Set.of(
            "thue kho", "cho thue kho", "kho thue", "chinh sach", "hop dong", "gia han",
            "quy trinh", "quy dinh",
            "dieu khoan", "dat coc", "hoan coc", "bao hiem", "boi thuong", "huy hop dong",
            "cham dut", "thanh ly hop dong", "huy thue", "tien coc", "thanh toan tien thue", "phi phat", "trach nhiem",
            "khi nao duoc thue", "thu tuc thue", "thoi gian thue", "thoi han thue",
            "dang ky thue", "cach thue", "phuong thuc thue", "yeu cau thue", "dieu kien thue",
            "don thue", "phi luu kho", "muon thue", "can thue", "cho thue", "thue theo thang",
            "thue theo m2", "thue ngan han", "thue dai han", "cuoc thue", "don gia thue",
            "muc thue"
    );

    private RentalIntentClassifier() {
    }

    public static Intent classify(String message) {
        String text = normalize(message);
        if (text.isBlank() || isWarehouseLookup(text) || containsAny(text, DIMENSION_WORDS)) {
            return Intent.none();
        }

        if (containsAny(text, SUBSCRIPTION_WORDS)) {
            return new Intent(Route.MY_ACTIVE_SUBSCRIPTION, null);
        }
        if (containsAny(text, PACKAGE_WORDS)) {
            return new Intent(Route.SERVICE_PACKAGES, null);
        }
        if (containsAny(text, CURRENT_RULE_WORDS)) {
            return new Intent(Route.CURRENT_SYSTEM_RULES, null);
        }
        if (containsAny(text, CONTRACT_WORDS)
                && containsAny(text, PRIVATE_CONTRACT_MARKERS)) {
            return new Intent(Route.MY_CONTRACTS, null);
        }
        if (containsAny(text, Set.of("hien tai", "moi nhat"))
                && hasRentalAnchor(text)) {
            return new Intent(Route.CURRENT_SYSTEM_RULES, null);
        }

        if (containsAny(text, Set.of(
                "bao hiem", "boi thuong", "den bu", "thiet hai", "mat mat", "hu hong"))) {
            return new Intent(Route.SYSTEM_POLICY, "INSURANCE");
        }
        if (containsAny(text, Set.of(
                "huy hop dong", "huy thue", "huy hop tac", "cham dut", "thanh ly hop dong"))
                || (text.contains("huy") && hasRentalAnchor(text))) {
            return new Intent(Route.SYSTEM_POLICY, "CANCELLATION");
        }
        if (containsAny(text, Set.of(
                "quy trinh", "thu tuc", "dat coc", "hoan coc", "khi nao duoc thue"))) {
            return new Intent(Route.SYSTEM_POLICY, "RENTAL_PROCESS");
        }
        if (containsAny(text, POLICY_WORDS)) {
            return new Intent(Route.SYSTEM_POLICY, null);
        }
        return Intent.none();
    }

    public static boolean isRentalQuestion(String message) {
        return classify(message).route() != Route.NONE;
    }

    private static boolean isWarehouseLookup(String text) {
        boolean mentionsWarehouse = text.contains("kho");
        if (!mentionsWarehouse) {
            return false;
        }
        boolean hasLocation = text.contains(" o ")
                || text.contains(" tai ")
                || text.contains(" quan ")
                || text.contains(" tinh ");
        return containsAny(text, SEARCH_WORDS)
                || text.contains("gia duoi")
                || text.contains("gia tren")
                || text.contains("kho dang cho thue")
                || (text.contains("xem kho") && !text.contains("ton kho"))
                || ((text.contains("can thue kho") || text.contains("muon thue kho"))
                && hasLocation)
                || (containsAny(text, Set.of(
                "gia thue kho", "phi thue kho", "don gia thue kho", "muc gia thue kho"))
                && !containsAny(text, Set.of(
                "quy dinh", "quy trinh", "dieu khoan", "dat coc", "tien coc",
                "bao hiem", "boi thuong", "huy", "thanh toan", "phi phat")));
    }

    private static boolean containsAny(String text, Set<String> phrases) {
        return phrases.stream().anyMatch(text::contains);
    }

    private static boolean hasRentalAnchor(String text) {
        return containsAny(text, Set.of(
                "thue", "hop dong", "chinh sach", "quy dinh", "phi", "kiem dinh",
                "xac nhan", "goi dich vu", "dat coc", "bao hiem"
        ));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("");
        return NON_WORD.matcher(withoutDiacritics
                        .replace('đ', 'd')
                        .replace('Đ', 'D')
                        .replace('²', '2')
                        .replace('³', '3')
                        .toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim();
    }

    public enum Route {
        NONE,
        SYSTEM_POLICY,
        CURRENT_SYSTEM_RULES,
        SERVICE_PACKAGES,
        MY_ACTIVE_SUBSCRIPTION,
        MY_CONTRACTS
    }

    public record Intent(Route route, String category) {
        public static Intent none() {
            return new Intent(Route.NONE, null);
        }

        public String requiredTool() {
            return switch (route) {
                case SYSTEM_POLICY -> "searchSystemPolicy";
                case CURRENT_SYSTEM_RULES -> "getCurrentSystemRules";
                case SERVICE_PACKAGES -> "getServicePackages";
                case MY_ACTIVE_SUBSCRIPTION -> "getMyActiveSubscription";
                case MY_CONTRACTS -> "getMyContracts";
                case NONE -> null;
            };
        }
    }
}

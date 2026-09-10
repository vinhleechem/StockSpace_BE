package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.chatbot.tool.ChatTool;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchWarehousesTool implements ChatTool {

    private static final int DEFAULT_RESULT_LIMIT = 5;
    private static final int MAX_RESULT_LIMIT = 20;
    private static final int FALLBACK_RESULT_LIMIT = 8;
    private static final int NORMALIZED_SEARCH_CANDIDATE_LIMIT = 200;
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    /**
     * A follow-up such as "Kho A bao nhiêu m2" is an entity lookup plus an
     * area question.  Passing the question words into a SQL LIKE predicate
     * makes an otherwise valid warehouse look missing, so strip only the
     * well-known dimension phrases before searching.  The original phrase is
     * still returned to the model as {@code requestedKeyword}.
     */
    private static final Pattern DIMENSION_QUESTION = Pattern.compile(
            "(?iu)(?:\\b(?:diện\\s+tích|dien\\s+tich|kích\\s+thước|kich\\s+thuoc)\\b"
                    + "(?:\\s+bao\\s+nhiêu(?:\\s*(?:m2|m²|mét\\s+vuông|met\\s+vuong))?)?\\b"
                    + "|\\bbao\\s+nhiêu\\s*(?:m2|m²|mét\\s+vuông|met\\s+vuong)\\b"
                    + "|\\b(?:rộng|rong|dài|dai|cao)\\s+bao\\s+nhiêu\\b"
                    + "|\\b(?:chiều\\s+dài|chieu\\s+dai)\\b)"
    );
    private static final Pattern ENTITY_QUESTION_NOISE = Pattern.compile(
            "(?iu)(?:\\b(?:giá|gia|phí|phi|chi\\s+phí|chi\\s+phi)"
                    + "(?:\\s+(?:thuê|thue))?\\s+bao\\s+nhiêu\\b"
                    + "|\\bbao\\s+nhiêu\\s+(?:tiền|tien|đồng|dong)\\b"
                    + "|\\b(?:còn|con)\\s+(?:không|khong)\\b"
                    + "|\\b(?:có\\s+sẵn|co\\s+san)\\s+(?:không|khong)\\b"
                    + "|\\b(?:ở\\s+đâu|o\\s+dau|tại\\s+đâu|tai\\s+dau)\\b"
                    + "|\\b(?:như\\s+thế\\s+nào|nhu\\s the\\s nao)\\b)"
    );
    private static final Set<String> SEMANTIC_STOP_WORDS = Set.of(
            "ai", "ban", "bao", "bao nhieu", "cach", "can", "cho", "co", "cua",
            "de", "gi", "giup", "hay", "hoi", "khong", "la", "minh", "muon",
            "nao", "neu", "nhu", "nha", "o", "phu", "toi", "tim", "toi can",
            "kho", "bai", "luu", "tru", "hang", "hoa", "va", "ve", "voi", "xin", "xem"
    );
    private static final List<SemanticConcept> SEMANTIC_CONCEPTS = List.of(
            new SemanticConcept(
                    Set.of("kho lanh", "dong lanh", "kho dong", "kho mat", "bao quan", "bao quan thuc pham", "chuoi lanh", "nhiet do", "2 8"),
                    Set.of("kho lanh", "dong lanh", "kho dong", "bao quan thuc pham", "thuc pham", "nong san", "nhiet do")
            ),
            new SemanticConcept(
                    Set.of("dien tu", "linh kien dien tu", "linh kien", "thiet bi dien", "hang cong nghe"),
                    Set.of("dien tu", "linh kien", "thiet bi dien", "hang cong nghe", "may moc")
            ),
            new SemanticConcept(
                    Set.of("nong san", "rau cu", "trai cay", "thuc pham tuoi", "hai san", "luong thuc"),
                    Set.of("nong san", "rau cu", "trai cay", "thuc pham", "bao quan", "hai san", "luong thuc")
            ),
            new SemanticConcept(
                    Set.of("vat lieu xay dung", "nguyen vat lieu", "sat thep", "xi mang", "gach"),
                    Set.of("vat lieu xay dung", "nguyen vat lieu", "sat thep", "hang nang", "xi mang", "gach")
            ),
            new SemanticConcept(
                    Set.of("pallet", "ke hang", "gia ke"),
                    Set.of("pallet", "ke hang", "gia ke", "kho hang")
            ),
            new SemanticConcept(
                    Set.of("thuong mai dien tu", "dong goi", "xu ly don", "hang tieu dung"),
                    Set.of("thuong mai dien tu", "dong goi", "xu ly don", "hang tieu dung", "kho hang")
            )
    );

    private final WarehouseRepository warehouseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "searchWarehouses";
    }

    @Override
    public String getDescription() {
        return "Tìm các bài đăng kho còn hiệu lực theo từ khóa, giá niêm yết, sức chứa và trạng thái xác minh. "
                + "Từ khóa có thể là tên, địa chỉ, tỉnh/thành, quận/huyện, loại kho hoặc nhu cầu lưu trữ. "
                + "Có thể lọc riêng tỉnh/thành, quận/huyện và cách tính giá, rồi sắp xếp theo giá hoặc sức chứa. "
                + "Giá niêm yết có thể là giá cố định theo tháng, giá mỗi m² mỗi tháng hoặc để thỏa thuận; "
                + "nếu người dùng không nêu tiêu chí, gọi với tham số rỗng để lấy các kho đang công khai. "
                + "Tìm kiếm có thể hiểu nhu cầu lưu trữ gần nghĩa (ví dụ kho lạnh với bảo quản thực phẩm, "
                + "nông sản hoặc đông lạnh) và xếp hạng kết quả gần đúng; hãy kiểm tra mô tả trước khi kết luận. "
                + "Nếu cần diện tích hoặc kích thước, dùng warehouseId trong kết quả để gọi getPublicWarehouseLayout; "
                + "không dùng capacity hay giá/m² để suy ra diện tích.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description",
                "Tên, địa chỉ, tỉnh/thành, quận/huyện, loại kho hoặc loại hàng cần lưu trữ; không đưa các từ hỏi diện tích như bao nhiêu m2 vào keyword"));
        properties.put("province", Map.of("type", "string", "description",
                "Tỉnh/thành phố cần lọc; có thể nhập một phần tên"));
        properties.put("district", Map.of("type", "string", "description",
                "Quận/huyện cần lọc; có thể nhập một phần tên"));
        properties.put("pricingType", Map.of("type", "string", "enum",
                List.of("FIXED_MONTHLY", "PER_SQUARE_METER_MONTHLY", "NEGOTIATED"),
                "description", "Cách tính giá thuê"));
        properties.put("minRentalPrice", Map.of("type", "number", "description", "Giá niêm yết tối thiểu"));
        properties.put("maxRentalPrice", Map.of("type", "number", "description", "Giá niêm yết tối đa"));
        properties.put("minCapacity", Map.of("type", "number", "description", "Sức chứa tối thiểu"));
        properties.put("maxCapacity", Map.of("type", "number", "description", "Sức chứa tối đa"));
        properties.put("isVerified", Map.of("type", "boolean", "description", "Chỉ lấy kho đã xác minh"));
        properties.put("sortBy", Map.of("type", "string", "enum",
                List.of("RELEVANCE", "PRICE_ASC", "PRICE_DESC", "CAPACITY_ASC", "CAPACITY_DESC", "NEWEST"),
                "description", "Cách sắp xếp kết quả"));
        properties.put("page", Map.of("type", "integer", "minimum", 0));
        properties.put("pageSize", Map.of("type", "integer", "minimum", 1, "maximum", MAX_RESULT_LIMIT));
        return Map.of("type", "object", "properties", properties);
    }

    @Override
    @Transactional(readOnly = true)
    public String execute(Map<String, Object> params, UUID userId) {
        try {
            Map<String, Object> safeParams = params == null ? Map.of() : params;
            String requestedKeyword = getStringParam(safeParams, "keyword");
            String keyword = cleanEntitySearchKeyword(requestedKeyword);
            String province = getLikeStringParam(safeParams, "province");
            String district = getLikeStringParam(safeParams, "district");
            RentalPricingType pricingType = getPricingTypeParam(safeParams, "pricingType");
            BigDecimal minPrice = getNonNegativeDecimalParam(safeParams, "minRentalPrice");
            BigDecimal maxPrice = getNonNegativeDecimalParam(safeParams, "maxRentalPrice");
            BigDecimal minCapacity = getNonNegativeDecimalParam(safeParams, "minCapacity");
            BigDecimal maxCapacity = getNonNegativeDecimalParam(safeParams, "maxCapacity");
            Boolean isVerified = getBooleanParam(safeParams, "isVerified");
            int page = ChatToolParameters.page(safeParams);
            int pageSize = ChatToolParameters.pageSize(safeParams, DEFAULT_RESULT_LIMIT, MAX_RESULT_LIMIT);
            Sort sort = sortParam(safeParams, "sortBy");
            validateRange(minPrice, maxPrice, "Giá niêm yết tối thiểu không được lớn hơn giá niêm yết tối đa");
            validateRange(minCapacity, maxCapacity, "Sức chứa tối thiểu không được lớn hơn sức chứa tối đa");

            Page<Warehouse> results = search(
                    keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%",
                    province, district, pricingType,
                    minPrice, maxPrice, minCapacity, maxCapacity, isVerified, page, pageSize, sort);

            if (results.getTotalElements() == 0 && keyword != null
                    && keyword.toLowerCase(Locale.ROOT).startsWith("kho ")) {
                String strippedKeyword = keyword.substring(4).trim();
                if (!strippedKeyword.isBlank()) {
                    results = search("%" + strippedKeyword.toLowerCase(Locale.ROOT) + "%",
                            province, district, pricingType,
                            minPrice, maxPrice, minCapacity, maxCapacity, isVerified, page, pageSize, sort);
                }
            }

            if (page == 0 && results.getTotalElements() == 0 && keyword != null) {
                Page<Warehouse> normalizedMatches = searchByNormalizedText(
                        keyword, province, district, pricingType,
                        minPrice, maxPrice, minCapacity, maxCapacity, isVerified,
                        pageSize, sort);
                if (!normalizedMatches.isEmpty()) {
                    Map<String, Object> response = baseResponse(normalizedMatches);
                    response.put("matchedByExactKeyword", false);
                    response.put("matchedByNormalizedKeyword", true);
                    response.put("matchedBySemanticKeyword", true);
                    response.put("matchMode", "SEMANTIC_FALLBACK");
                    response.put("requestedKeyword", requestedKeyword);
                    addSearchKeywordMetadata(response, requestedKeyword, keyword);
                    response.put("approximateCandidateLimit", NORMALIZED_SEARCH_CANDIDATE_LIMIT);
                    response.put("guidance",
                            "Kết quả được tìm bằng chuẩn hóa, từ đồng nghĩa hoặc gần đúng trên nhóm kho khả dụng gần nhất; hãy kiểm tra lại tên, địa chỉ và mô tả trước khi chọn.");
                    return objectMapper.writeValueAsString(response);
                }

                Page<Warehouse> fallback = search(null, province, district, pricingType,
                        minPrice, maxPrice, minCapacity, maxCapacity, isVerified,
                        0, Math.max(pageSize, FALLBACK_RESULT_LIMIT), sort);
                if (!fallback.isEmpty()) {
                    Map<String, Object> response = baseResponse(fallback);
                    response.put("matchedByExactKeyword", false);
                    response.put("matchMode", "FALLBACK_LIST");
                    response.put("requestedKeyword", requestedKeyword);
                    addSearchKeywordMetadata(response, requestedKeyword, keyword);
                    response.put("guidance",
                            "Không có kết quả khớp chính xác; hãy so sánh mô tả, loại kho và vị trí trước khi gợi ý.");
                    return objectMapper.writeValueAsString(response);
                }
            }

            Map<String, Object> response = baseResponse(results);
            addSearchKeywordMetadata(response, requestedKeyword, keyword);
            if (requestedKeyword != null) {
                response.put("matchedByExactKeyword", !results.isEmpty());
                response.put("matchMode", results.isEmpty() ? "NO_MATCH" : "EXACT");
            }
            if (results.isEmpty()) {
                response.put("message", "Không tìm thấy bài đăng kho còn hiệu lực phù hợp với bộ lọc.");
                response.put("guidance",
                        "Không có kết quả với từ khóa này; điều đó chưa chứng minh kho đã bị xóa hoặc không còn khả dụng. Hãy thử lại bằng tên kho ngắn hơn hoặc xác nhận tên kho.");
            }
            return objectMapper.writeValueAsString(response);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        } catch (Exception e) {
            log.warn("[SearchWarehousesTool] Search failed", e);
            return error("Không thể tìm kiếm kho lúc này.");
        }
    }

    private Page<Warehouse> search(String keyword, String province, String district,
                                   RentalPricingType pricingType,
                                   BigDecimal minPrice, BigDecimal maxPrice,
                                   BigDecimal minCapacity, BigDecimal maxCapacity,
                                   Boolean isVerified, int page, int limit, Sort sort) {
        PageRequest pageable = PageRequest.of(page, limit, sort);
        if (province == null && district == null && pricingType == null) {
            return warehouseRepository.searchPublic(
                    keyword, WarehouseStatus.AVAILABLE, minPrice, maxPrice, minCapacity, maxCapacity,
                    null, null, null, isVerified, pageable);
        }
        return warehouseRepository.searchPublicForChat(
                keyword, province, district, pricingType,
                minPrice, maxPrice, minCapacity, maxCapacity, isVerified, pageable);
    }

    private String cleanEntitySearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String cleaned = DIMENSION_QUESTION.matcher(keyword).replaceAll(" ");
        cleaned = ENTITY_QUESTION_NOISE.matcher(cleaned).replaceAll(" ")
                .replaceAll("[?!,:;]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // Do not turn a standalone dimension question into a broad listing.
        return cleaned.isBlank() ? keyword.trim() : cleaned;
    }

    private void addSearchKeywordMetadata(Map<String, Object> response,
                                          String requestedKeyword,
                                          String searchedKeyword) {
        if (requestedKeyword != null && searchedKeyword != null
                && !requestedKeyword.equals(searchedKeyword)) {
            response.put("searchedKeyword", searchedKeyword);
            response.put("keywordIntentRemoved", true);
        }
    }

    private Page<Warehouse> searchByNormalizedText(String keyword,
                                                   String province,
                                                   String district,
                                                   RentalPricingType pricingType,
                                                   BigDecimal minPrice,
                                                   BigDecimal maxPrice,
                                                   BigDecimal minCapacity,
                                                   BigDecimal maxCapacity,
                                                   Boolean isVerified,
                                                   int pageSize,
                                                   Sort sort) {
        Page<Warehouse> candidates = search(
                null, province, district, pricingType,
                minPrice, maxPrice, minCapacity, maxCapacity, isVerified,
                0, NORMALIZED_SEARCH_CANDIDATE_LIMIT, sort);
        SemanticQuery semanticQuery = buildSemanticQuery(keyword);
        if (semanticQuery.normalized().isBlank()
                || (semanticQuery.queryTerms().isEmpty()
                && semanticQuery.expandedPhrases().isEmpty())) {
            return Page.empty(PageRequest.of(0, pageSize, sort));
        }
        List<ScoredWarehouse> scored = candidates.getContent().stream()
                .map(warehouse -> new ScoredWarehouse(
                        warehouse,
                        semanticScore(warehouse, semanticQuery)))
                .filter(match -> match.score() >= 0.15)
                .sorted(Comparator.comparingDouble(ScoredWarehouse::score).reversed())
                .toList();
        List<Warehouse> pageContent = scored.stream()
                .limit(pageSize)
                .map(ScoredWarehouse::warehouse)
                .toList();
        return new PageImpl<>(pageContent, PageRequest.of(0, pageSize, sort), scored.size());
    }

    private SemanticQuery buildSemanticQuery(String keyword) {
        String normalizedKeyword = normalizeSearchText(keyword);
        List<String> queryTerms = normalizedKeyword.isBlank()
                ? List.of()
                : Arrays.stream(normalizedKeyword.split("\\s+"))
                .filter(term -> term.length() >= 2 && !SEMANTIC_STOP_WORDS.contains(term))
                .toList();
        LinkedHashSet<String> expandedPhrases = new LinkedHashSet<>();
        for (SemanticConcept concept : SEMANTIC_CONCEPTS) {
            boolean triggered = concept.triggers().stream()
                    .anyMatch(normalizedKeyword::contains);
            if (triggered) {
                expandedPhrases.addAll(concept.expansions());
            }
        }
        return new SemanticQuery(
                normalizedKeyword,
                queryTerms,
                List.copyOf(expandedPhrases));
    }

    private double semanticScore(Warehouse warehouse, SemanticQuery query) {
        String name = normalizeSearchText(safeText(warehouse.getName()));
        String searchable = normalizeSearchText(String.join(" ",
                safeText(warehouse.getName()),
                safeText(warehouse.getAddress()),
                safeText(warehouse.getProvinceName()),
                safeText(warehouse.getDistrictName()),
                safeText(warehouse.getDescription()),
                warehouse.getType() == null ? "" : safeText(warehouse.getType().getName())));
        if (query.normalized().length() >= 3 && searchable.contains(query.normalized())) {
            return 1.0;
        }
        if (searchable.isBlank()) {
            return 0.0;
        }

        String[] searchableTerms = searchable.split("\\s+");
        long directMatches = query.queryTerms().stream()
                .filter(queryTerm -> Arrays.stream(searchableTerms)
                        .anyMatch(candidate -> approximateTermMatch(queryTerm, candidate)))
                .count();
        double directCoverage = query.queryTerms().isEmpty()
                ? 0.0
                : (double) directMatches / query.queryTerms().size();
        long titleMatches = query.queryTerms().stream()
                .filter(queryTerm -> Arrays.stream(name.split("\\s+"))
                        .anyMatch(candidate -> approximateTermMatch(queryTerm, candidate)))
                .count();
        double titleCoverage = query.queryTerms().isEmpty()
                ? 0.0
                : (double) titleMatches / query.queryTerms().size();
        long conceptMatches = query.expandedPhrases().stream()
                .filter(searchable::contains)
                .count();
        double conceptStrength = Math.min(1.0, conceptMatches / 2.0);
        if (directMatches == 0 && conceptMatches == 0) {
            return 0.0;
        }
        return Math.min(1.0,
                0.55 * directCoverage
                        + 0.35 * conceptStrength
                        + 0.10 * titleCoverage);
    }

    private boolean approximateTermMatch(String queryTerm, String candidate) {
        if (candidate.contains(queryTerm) || queryTerm.contains(candidate)) {
            return true;
        }
        if (queryTerm.length() < 3 || candidate.length() < 3) {
            return false;
        }
        int maxDistance = queryTerm.length() >= 7 ? 2 : 1;
        return levenshteinDistance(queryTerm, candidate, maxDistance) <= maxDistance;
    }

    private int levenshteinDistance(String left, String right, int cutoff) {
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

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutDiacritics = DIACRITICS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd')
                .replace('Đ', 'D');
        return NON_WORD.matcher(withoutDiacritics.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private Map<String, Object> baseResponse(Page<Warehouse> page) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotalElements());
        result.put("page", page.getNumber());
        result.put("pageSize", page.getSize());
        result.put("totalPages", page.getTotalPages());
        result.put("hasMore", !page.isLast());
        result.put("warehouses", page.getContent().stream().map(this::toMap).toList());
        return result;
    }

    private Map<String, Object> toMap(Warehouse warehouse) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", warehouse.getId());
        result.put("name", warehouse.getName());
        result.put("address", warehouse.getAddress());
        result.put("province", warehouse.getProvinceName());
        result.put("district", warehouse.getDistrictName());
        result.put("description", warehouse.getDescription());
        result.put("capacity", warehouse.getCapacity());
        result.put("capacityNote", "Sức chứa khai báo của bài đăng; không phải diện tích (m²)");
        result.put("pricingType", ChatToolLocalization.rentalPricingType(warehouse.getRentalPricingType()));
        result.put("listedRentalPrice", warehouse.getRentalPrice());
        result.put("priceUnit", priceUnit(warehouse.getRentalPricingType()));
        result.put("type", warehouse.getType() == null ? null : warehouse.getType().getName());
        result.put("verified", warehouse.isVerified());
        result.put("listingVisibleUntil", warehouse.getVisibleUntil());
        result.put("status", ChatToolLocalization.warehouseStatus(warehouse.getStatus()));
        return result;
    }

    private String priceUnit(RentalPricingType pricingType) {
        if (pricingType == null) {
            return null;
        }
        return switch (pricingType) {
            case FIXED_MONTHLY -> "VND/tháng";
            case PER_SQUARE_METER_MONTHLY -> "VND/m²/tháng";
            case NEGOTIATED -> "Thỏa thuận";
        };
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private String getLikeStringParam(Map<String, Object> params, String key) {
        String value = getStringParam(params, key);
        return value == null ? null : "%" + value.toLowerCase(Locale.ROOT) + "%";
    }

    private RentalPricingType getPricingTypeParam(Map<String, Object> params, String key) {
        String raw = getStringParam(params, key);
        if (raw == null) {
            return null;
        }
        try {
            return RentalPricingType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Cách tính giá thuê không hợp lệ: FIXED_MONTHLY, PER_SQUARE_METER_MONTHLY hoặc NEGOTIATED");
        }
    }

    private Sort sortParam(Map<String, Object> params, String key) {
        String raw = getStringParam(params, key);
        String value = raw == null ? "RELEVANCE" : raw.toUpperCase(Locale.ROOT);
        return switch (value) {
            case "RELEVANCE", "NEWEST" -> Sort.by(Sort.Direction.DESC, "publishedAt");
            case "PRICE_ASC" -> Sort.by(Sort.Direction.ASC, "rentalPrice")
                    .and(Sort.by(Sort.Direction.DESC, "publishedAt"));
            case "PRICE_DESC" -> Sort.by(Sort.Direction.DESC, "rentalPrice")
                    .and(Sort.by(Sort.Direction.DESC, "publishedAt"));
            case "CAPACITY_ASC" -> Sort.by(Sort.Direction.ASC, "capacity")
                    .and(Sort.by(Sort.Direction.DESC, "publishedAt"));
            case "CAPACITY_DESC" -> Sort.by(Sort.Direction.DESC, "capacity")
                    .and(Sort.by(Sort.Direction.DESC, "publishedAt"));
            default -> throw new IllegalArgumentException(
                    "Cách sắp xếp không hợp lệ: RELEVANCE, PRICE_ASC, PRICE_DESC, CAPACITY_ASC, CAPACITY_DESC hoặc NEWEST");
        };
    }

    private BigDecimal getNonNegativeDecimalParam(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(raw.toString());
            if (value.signum() < 0) {
                throw new IllegalArgumentException(ChatToolLocalization.filterLabel(key) + " không được là số âm");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(ChatToolLocalization.filterLabel(key) + " phải là một số hợp lệ");
        }
    }

    private Boolean getBooleanParam(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean value) {
            return value;
        }
        if (raw instanceof String value && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            return Boolean.valueOf(value);
        }
        throw new IllegalArgumentException("Trạng thái xác minh phải là true hoặc false");
    }

    private void validateRange(BigDecimal minimum, BigDecimal maximum, String message) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(Map.of("error", message));
        } catch (Exception ignored) {
            return "{\"error\":\"Không thể tìm kiếm kho lúc này.\"}";
        }
    }

    private record SemanticConcept(Set<String> triggers, Set<String> expansions) {
    }

    private record SemanticQuery(
            String normalized,
            List<String> queryTerms,
            List<String> expandedPhrases
    ) {
    }

    private record ScoredWarehouse(Warehouse warehouse, double score) {
    }
}

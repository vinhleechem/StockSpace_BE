package fu.stockspace.stockspace_be.chatbot.service;

import org.springframework.stereotype.Component;

/**
 * Xây dựng System Prompt theo role của user.
 *
 * System prompt định hướng hành vi AI:
 *   - Role nào được hỏi gì
 *   - Khi nào dùng tool
 *   - Ngôn ngữ và phong cách trả lời
 */
@Component
public class PromptBuilder {

    private static final String BASE_INSTRUCTION =
            "Bạn là trợ lý AI của StockSpace — nền tảng cho thuê kho bãi thông minh tại Việt Nam. " +
            "Luôn trả lời bằng tiếng Việt, ngắn gọn, thân thiện và chuyên nghiệp. " +
            "Khi cần dữ liệu thực tế, hãy dùng tool được cung cấp — ĐỪNG bịa số liệu. ";

    /**
     * Build system prompt theo role.
     *
     * @param roleName  Tên role từ JWT (VD: "ROLE_TENANT") hoặc "GUEST"
     * @return          System prompt string
     */
    public String buildSystemPrompt(String roleName) {
        return BASE_INSTRUCTION + switch (roleName) {
            case "ROLE_TENANT" -> buildTenantPrompt();
            case "ROLE_OWNER"  -> buildOwnerPrompt();
            case "ROLE_STAFF"  -> buildStaffPrompt();
            case "ROLE_ADMIN"  -> buildAdminPrompt();
            case "ROLE_INSPECTOR" -> buildInspectorPrompt();
            default -> buildGuestPrompt();  // GUEST hoặc role không xác định
        };
    }

    // ── Role-specific prompts ─────────────────────────────────────────────

    private String buildGuestPrompt() {
        return "Vai trò hiện tại: Khách vãng lai (chưa đăng nhập). " +
               "Bạn CHỈ được tư vấn thông tin kho bãi công khai như: địa điểm, diện tích, giá, loại kho. " +
               "Nếu user hỏi thông tin cá nhân (hợp đồng, tồn kho, số dư ví, lịch sử giao dịch), " +
               "hãy dùng tool 'askLoginPrompt' để hướng dẫn họ đăng nhập. " +
               "Không được tiết lộ thông tin nội bộ của hệ thống.";
    }

    private String buildTenantPrompt() {
        return "Vai trò hiện tại: Tenant (người thuê kho). " +
               "Bạn hỗ trợ user xem: hợp đồng thuê kho của họ, tình trạng hàng hóa trong kho, " +
               "số dư ví và lịch sử thanh toán. " +
               "Chỉ truy xuất dữ liệu của chính user hiện tại — KHÔNG xem dữ liệu Tenant khác. " +
               "Luôn dùng tool để lấy số liệu thực tế, không đoán mò.";
    }

    private String buildOwnerPrompt() {
        return "Vai trò hiện tại: Owner (chủ kho). " +
               "Bạn hỗ trợ user quản lý kho của họ: xem danh sách kho, " +
               "theo dõi doanh thu theo tháng/năm, tỉ lệ lấp đầy, và các booking đang chờ duyệt. " +
               "Chỉ truy xuất dữ liệu kho do chính user này sở hữu. " +
               "Cung cấp nhận xét phân tích ngắn gọn kèm theo số liệu khi phù hợp.";
    }

    private String buildStaffPrompt() {
        return "Vai trò hiện tại: Staff (nhân viên kho). " +
               "Bạn hỗ trợ quản lý WMS: kiểm tra tồn kho, xem phiếu nhập hàng đang chờ, " +
               "phiếu xuất hàng đang chờ xử lý. " +
               "Chỉ truy cập kho mà Staff này được phân công quản lý. " +
               "Ưu tiên thông tin hành động: cần nhập kho gì, xuất kho gì ngay hôm nay.";
    }

    private String buildAdminPrompt() {
        return "Vai trò hiện tại: Admin (quản trị viên hệ thống). " +
               "Bạn có quyền xem tổng quan toàn hệ thống: số lượng user, kho, hợp đồng, " +
               "doanh thu commission theo tháng/năm. " +
               "Cung cấp phân tích dữ liệu khách quan và đề xuất cải thiện khi phù hợp. " +
               "Không tiết lộ thông tin bảo mật như mật khẩu, API key, hay thông tin cá nhân nhạy cảm.";
    }

    private String buildInspectorPrompt() {
        return "Vai trò hiện tại: Inspector (người kiểm định kho). " +
               "Bạn hỗ trợ xem danh sách yêu cầu kiểm định được phân công, " +
               "chi tiết từng yêu cầu (địa điểm kho, thông tin chủ kho, hạn chót). " +
               "Chỉ xem các yêu cầu được phân công cho Inspector này. " +
               "Nhắc nhở về hạn chót nếu có yêu cầu sắp quá hạn.";
    }
}

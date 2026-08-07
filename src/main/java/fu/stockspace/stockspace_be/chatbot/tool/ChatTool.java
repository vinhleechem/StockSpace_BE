package fu.stockspace.stockspace_be.chatbot.tool;

import java.util.Map;
import java.util.UUID;

/**
 * Interface chuẩn cho tất cả AI Tools.
 *
 * ⚠️ Dev B implement tools của mình dựa trên interface này.
 *
 * Quy ước đặt tên:
 *   - getName()        → camelCase, VD: "searchWarehouses"
 *   - execute()        → trả về JSON string để Gemini xử lý tiếp
 *   - userId = null    → tool của GUEST (không cần đăng nhập)
 */
public interface ChatTool {

    /**
     * Tên tool — Gemini dùng tên này để gọi tool.
     * VD: "searchWarehouses", "getMyContracts"
     */
    String getName();

    /**
     * Mô tả ngắn để Gemini biết khi nào nên dùng tool này.
     */
    String getDescription();

    /**
     * JSON Schema mô tả tham số tool nhận vào.
     * Format: {"type":"OBJECT","properties":{...},"required":[...]}
     */
    Map<String, Object> getParameterSchema();

    /**
     * Thực thi tool và trả về kết quả dưới dạng JSON string.
     *
     * @param params  Tham số do Gemini truyền vào (từ function_call.args)
     * @param userId  UUID của user đang chat (null nếu GUEST)
     * @return        JSON string kết quả để gửi lại cho Gemini
     */
    String execute(Map<String, Object> params, UUID userId);
}

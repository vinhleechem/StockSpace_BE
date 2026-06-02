package fu.stockspace.stockspace_be.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic response wrapper — toàn bộ API trong hệ thống đều trả về format này.
 *
 * Ví dụ thành công:
 * { "success": true, "message": "Login successful", "data": { ... } }
 *
 * Ví dụ lỗi:
 * { "success": false, "message": "Invalid credentials", "data": null }
 *
 * @param <T> kiểu dữ liệu của data field
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    // ==================== Factory methods ====================

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("Success", data);
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}

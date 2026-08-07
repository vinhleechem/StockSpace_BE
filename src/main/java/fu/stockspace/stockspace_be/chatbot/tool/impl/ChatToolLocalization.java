package fu.stockspace.stockspace_be.chatbot.tool.impl;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;

import java.util.Locale;

/**
 * Localizes domain values at the chatbot boundary without changing the enums
 * used by repositories and business services.
 */
final class ChatToolLocalization {

    private ChatToolLocalization() {
    }

    static String contractStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "Không xác định";
        }

        return switch (rawStatus.trim().toUpperCase(Locale.ROOT)) {
            case "UNDER_NEGOTIATION" -> "Đang thương lượng";
            case "PENDING_TENANT_CONFIRM" -> "Chờ người thuê xác nhận";
            case "ACTIVE" -> "Đang có hiệu lực";
            case "PENDING_CANCEL" -> "Chờ phản hồi yêu cầu hủy";
            case "CANCELLED" -> "Đã hủy";
            case "PENDING_HANDOVER" -> "Chờ xác nhận bàn giao";
            case "COMPLETED" -> "Đã hoàn tất";
            case "DISPUTED" -> "Đang tranh chấp";
            default -> "Không xác định";
        };
    }

    static String warehouseStatus(WarehouseStatus status) {
        if (status == null) {
            return "Không xác định";
        }

        return switch (status) {
            case AVAILABLE -> "Sẵn sàng cho thuê";
            case RENTED -> "Đang được thuê";
            case PENDING_APPROVAL -> "Chờ duyệt";
            case INACTIVE -> "Tạm ngừng cho thuê";
        };
    }

    static String filterLabel(String parameterName) {
        return switch (parameterName) {
            case "minPrice" -> "Giá thuê tối thiểu";
            case "maxPrice" -> "Giá thuê tối đa";
            case "minArea" -> "Diện tích tối thiểu";
            default -> "Giá trị bộ lọc";
        };
    }
}

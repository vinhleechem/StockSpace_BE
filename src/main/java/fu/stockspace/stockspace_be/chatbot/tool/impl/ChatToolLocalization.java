package fu.stockspace.stockspace_be.chatbot.tool.impl;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;

import java.util.Locale;





final class ChatToolLocalization {

    private ChatToolLocalization() {
    }

    static String contractStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "Không xác định";
        }

        return switch (rawStatus.trim().toUpperCase(Locale.ROOT)) {
            case "DRAFT" -> "Bản nháp";
            case "PENDING_TENANT_CONFIRM" -> "Chờ người thuê xác nhận";
            case "CHANGES_REQUESTED" -> "Người thuê yêu cầu chỉnh sửa";
            case "ACTIVE" -> "Đang có hiệu lực";
            case "REJECTED" -> "Đã từ chối";
            case "EXPIRED" -> "Đã hết hạn";
            default -> "Không xác định";
        };
    }

    static String warehouseStatus(WarehouseStatus status) {
        if (status == null) {
            return "Không xác định";
        }

        return switch (status) {
            case AVAILABLE -> "Sẵn sàng cho thuê";
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

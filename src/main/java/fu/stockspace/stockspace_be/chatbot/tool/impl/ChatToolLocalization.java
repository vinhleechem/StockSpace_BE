package fu.stockspace.stockspace_be.chatbot.tool.impl;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.capacity.CapacityStatus;

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
            case DRAFT -> "Bản nháp";
            case AVAILABLE -> "Sẵn sàng cho thuê";
            case PENDING_APPROVAL -> "Chờ duyệt";
            case INACTIVE -> "Tạm ngừng cho thuê";
        };
    }

    static String rentalPricingType(RentalPricingType pricingType) {
        if (pricingType == null) {
            return "Không xác định";
        }
        return switch (pricingType) {
            case FIXED_MONTHLY -> "Cố định theo tháng";
            case PER_SQUARE_METER_MONTHLY -> "Theo mét vuông mỗi tháng";
            case NEGOTIATED -> "Thỏa thuận trực tiếp";
        };
    }

    static String approvalStatus(ApprovalStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PENDING -> "Chờ duyệt";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Đã từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    static String auditStatus(AuditStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PENDING -> "Chờ kiểm kê";
            case DRAFT -> "Bản nháp";
            case IN_PROGRESS -> "Đang kiểm kê";
            case SUBMITTED -> "Đã gửi kết quả";
            case RECOUNT_REQUIRED -> "Yêu cầu kiểm kê lại";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Đã từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    static String transferStatus(StockTransferStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PENDING -> "Chờ duyệt xuất";
            case IN_TRANSIT -> "Đang vận chuyển";
            case COMPLETED -> "Đã nhận tại kho đích";
            case REJECTED -> "Đã từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    static String capacityStatus(CapacityStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case EMPTY -> "Đang trống";
            case AVAILABLE -> "Còn sức chứa";
            case FULL -> "Đã đầy";
            case OVER_CAPACITY -> "Vượt sức chứa";
        };
    }

    static String filterLabel(String parameterName) {
        return switch (parameterName) {
            case "minRentalPrice" -> "Giá niêm yết tối thiểu";
            case "maxRentalPrice" -> "Giá niêm yết tối đa";
            case "minCapacity" -> "Sức chứa tối thiểu";
            case "maxCapacity" -> "Sức chứa tối đa";
            default -> "Giá trị bộ lọc";
        };
    }
}

package fu.stockspace.stockspace_be.chatbot.tool.impl;

import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.common.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditStatus;
import fu.stockspace.stockspace_be.wms.stock.entity.AuditScopeType;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptStatus;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferAttemptType;
import fu.stockspace.stockspace_be.wms.capacity.CapacityStatus;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.wallet.entity.PaymentMethod;
import fu.stockspace.stockspace_be.wallet.entity.TransactionStatus;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;

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
            case "SCHEDULED" -> "Đã xác nhận, chờ ngày bắt đầu";
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
            case EDIT_REQUESTED -> "Yêu cầu chỉnh sửa";
            case REOPENED -> "Đang chỉnh sửa";
            case RECOUNT_REQUIRED -> "Yêu cầu kiểm kê lại";
            case APPROVED -> "Đã duyệt";
            case REJECTED -> "Đã từ chối";
            case CANCELLED -> "Đã hủy";
        };
    }

    static String auditScope(AuditScopeType scopeType) {
        if (scopeType == null) {
            return "Không xác định";
        }
        return switch (scopeType) {
            case WAREHOUSE -> "Toàn kho";
            case RACK -> "Theo kệ";
            case BIN -> "Theo ô chứa";
        };
    }

    static String transferStatus(StockTransferStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case DRAFT -> "Bản nháp";
            case OVERDUE -> "Quá SLA chưa nhận";
            case ARRIVED_AT_DESTINATION -> "Đã đến kho đích";
            case RECEIVING -> "Đang kiểm nhận";
            case SHORT_RECEIVED -> "Nhận thiếu đã đóng";
            case RECEIVE_REJECTED -> "Kho đích từ chối nhận";
            case RETRY_REQUESTED -> "Đang chờ chuyển lại";
            case RETURN_REQUESTED -> "Đang chờ quay đầu";
            case RETURN_IN_TRANSIT -> "Đang quay về kho nguồn";
            case PARTIALLY_RETURNED -> "Đã quay về một phần";
            case RETURNED -> "Đã quay về kho nguồn";
            case LOST -> "Đã xác nhận thất lạc";
            case PENDING -> "Chờ duyệt xuất";
            case ALLOCATED -> "Đã giữ tồn";
            case PICKING -> "Đang lấy hàng";
            case READY_TO_DISPATCH -> "Sẵn sàng xuất";
            case IN_TRANSIT -> "Đang vận chuyển";
            case PARTIALLY_RECEIVED -> "Đã nhận một phần";
            case RECONCILING -> "Đang đối soát chênh lệch";
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

    static String transferCommand(String command) {
        if (command == null || command.isBlank()) {
            return "Cập nhật hệ thống";
        }
        return switch (command.trim().toUpperCase(Locale.ROOT)) {
            case "APPROVE_DISPATCH" -> "Duyệt xuất chuyển kho";
            case "ALLOCATE" -> "Phân bổ tồn kho";
            case "PICK" -> "Xác nhận lấy hàng";
            case "ARRIVE" -> "Xác nhận đến kho đích";
            case "RECEIVE" -> "Kiểm nhận hàng";
            case "REJECT_RECEIPT" -> "Từ chối nhận hàng";
            case "CLOSE_SHORT" -> "Đóng xử lý nhận thiếu";
            case "RETRY", "DISPATCH_RETRY" -> "Yêu cầu chuyển lại";
            case "REQUEST_RETURN" -> "Yêu cầu quay đầu";
            case "DISPATCH_RETURN" -> "Xuất chuyến quay đầu";
            case "RECEIVE_RETURN" -> "Nhận hàng quay về";
            case "RECONCILE" -> "Đối soát chênh lệch";
            default -> "Cập nhật trạng thái";
        };
    }

    static String transferAttemptType(StockTransferAttemptType type) {
        if (type == null) {
            return "Lần chuyển khác";
        }
        return switch (type) {
            case OUTBOUND -> "Chuyến xuất ban đầu";
            case FORWARD -> "Chuyến chuyển tiếp";
            case RETURN -> "Chuyến quay đầu";
        };
    }

    static String transferAttemptStatus(StockTransferAttemptStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PLANNED -> "Đã lập kế hoạch";
            case IN_TRANSIT -> "Đang vận chuyển";
            case ARRIVED -> "Đã đến kho đích";
            case RECEIVING -> "Đang kiểm nhận";
            case RECEIVED -> "Đã nhận đủ";
            case REJECTED -> "Đã từ chối";
            case PARTIALLY_RECEIVED -> "Đã nhận một phần";
            case RETURNED -> "Đã quay về";
            case CANCELLED -> "Đã hủy";
        };
    }

    static String subscriptionStatus(SubscriptionStatus status) {
        if (status == null) {
            return "Không có gói đang hoạt động";
        }
        return switch (status) {
            case ACTIVE -> "Đang hoạt động";
            case EXPIRED -> "Đã hết hạn";
            case CANCELLED -> "Đã hủy";
            case SUPERSEDED -> "Đã thay thế";
        };
    }

    static String transactionStatus(TransactionStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PENDING -> "Đang chờ xử lý";
            case SUCCESS -> "Thành công";
            case FAILED -> "Thất bại";
            case EXPIRED -> "Đã hết hạn";
        };
    }

    static String transactionType(TransactionType type) {
        if (type == null) {
            return "Giao dịch khác";
        }
        return switch (type) {
            case TOP_UP -> "Nạp tiền vào ví";
            case WITHDRAWAL -> "Rút tiền";
            case DEPOSIT_PAYMENT -> "Thanh toán tiền cọc";
            case DEPOSIT_RECEIVED -> "Nhận tiền cọc";
            case DEPOSIT_REFUND -> "Hoàn tiền cọc";
            case PACKAGE_PAYMENT -> "Thanh toán gói dịch vụ";
            case COMMISSION -> "Hoa hồng";
            case LISTING_FEE -> "Phí đăng kho";
            case LISTING_REFUND -> "Hoàn phí đăng kho";
        };
    }

    static String paymentMethod(PaymentMethod method) {
        if (method == null) {
            return "Không xác định";
        }
        return switch (method) {
            case BANK_TRANSFER -> "Chuyển khoản ngân hàng";
            case VNPAY -> "VNPAY";
            case MOMO -> "MoMo";
            case WALLET -> "Ví StockSpace";
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

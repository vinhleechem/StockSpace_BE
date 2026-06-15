package fu.stockspace.stockspace_be.inspection.entity;

/**
 * Trạng thái của InspectionReport.
 *
 * PENDING     — Yêu cầu kiểm định đã gửi, chờ Admin gán Inspector
 * IN_PROGRESS — Inspector đã nhận và đang tiến hành kiểm định
 * PASSED      — Kiểm định đạt → Warehouse.isVerified = true + AVAILABLE
 * FAILED      — Kiểm định không đạt → Warehouse vẫn INACTIVE/PENDING
 */
public enum InspectionStatus {
    PENDING,
    IN_PROGRESS,
    PASSED,
    FAILED
}

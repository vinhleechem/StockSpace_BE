package fu.stockspace.stockspace_be.contract.entity;

/**
 * Trạng thái của RentalContract.
 *
 * UNDER_NEGOTIATION      — Đang thương lượng hợp đồng (Chủ kho và Người thuê tự liên hệ ngoài)
 * PENDING_TENANT_CONFIRM — Chủ kho đã tải hợp đồng lên hệ thống, chờ Người thuê xác nhận
 * ACTIVE                 — Hợp đồng đang có hiệu lực (Người thuê đang sử dụng kho)
 * PENDING_CANCEL         — Chủ kho đề xuất hủy deal/hợp đồng khi thương lượng, chờ Người thuê phản hồi
 * CANCELLED              — Hợp đồng đã được hủy (hai bên đồng ý hủy hoặc thỏa thuận thất bại)
 * PENDING_HANDOVER       — Chờ cả 2 bên xác nhận bàn giao/trả kho bãi khi kết thúc thuê
 * COMPLETED              — Cả 2 bên đã xác nhận bàn giao, hợp đồng kết thúc hoàn toàn, kho trả lại AVAILABLE
 * DISPUTED               — Có tranh chấp đang mở (DisputeTicket) được gửi lên hệ thống phân xử
 */
public enum ContractStatus {
    UNDER_NEGOTIATION,
    PENDING_TENANT_CONFIRM,
    ACTIVE,
    PENDING_CANCEL,
    CANCELLED,
    PENDING_HANDOVER,
    COMPLETED,
    DISPUTED
}

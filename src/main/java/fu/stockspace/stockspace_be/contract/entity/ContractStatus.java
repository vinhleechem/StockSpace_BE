package fu.stockspace.stockspace_be.contract.entity;

/**
 * Trạng thái của RentalContract.
 *
 * ACTIVE           — Hợp đồng đang hiệu lực
 * PENDING_HANDOVER — Chờ cả 2 bên xác nhận bàn giao
 * COMPLETED        — Cả 2 bên đã confirm, kho trả lại AVAILABLE
 * DISPUTED         — Có tranh chấp đang mở (DisputeTicket)
 */
public enum ContractStatus {
    ACTIVE,
    PENDING_HANDOVER,
    COMPLETED,
    DISPUTED
}

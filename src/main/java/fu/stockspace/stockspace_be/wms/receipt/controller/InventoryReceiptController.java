package fu.stockspace.stockspace_be.wms.receipt.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.CreateInventoryReceiptRequest;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.RejectReceiptRequest;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Tenant — WMS Inventory Receipt Management", description = "Các API WMS quản lý Phiếu nhập/xuất kho dành cho Tenant & Staff")
@RestController
@RequestMapping("/api/tenant/inventory/receipts")
@RequiredArgsConstructor
public class InventoryReceiptController {

    private final InventoryReceiptService receiptService;

    @PostMapping
    @PreAuthorize("@rbac.hasAnyPermission('INBOUND_CREATE', 'OUTBOUND_CREATE')")
    @Operation(summary = "Tạo phiếu nhập/xuất kho mới ở trạng thái PENDING")
    public ResponseEntity<ApiResponse<InventoryReceiptResponse>> createReceipt(
            @Valid @RequestBody CreateInventoryReceiptRequest request
    ) {
        UUID userId = SecurityUtil.getCurrentUserId();
        InventoryReceiptResponse response = receiptService.createReceipt(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo phiếu nhập/xuất kho thành công", response));
    }

    @PatchMapping("/{id}/approve")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Duyệt phiếu nhập/xuất kho (cộng/trừ kho và ghi nhật ký giao dịch)")
    public ResponseEntity<ApiResponse<InventoryReceiptResponse>> approveReceipt(@PathVariable UUID id) {
        UUID userId = SecurityUtil.getCurrentUserId();
        InventoryReceiptResponse response = receiptService.approveReceipt(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Duyệt phiếu thành công, kho hàng đã cập nhật", response));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Từ chối phiếu nhập/xuất kho")
    public ResponseEntity<ApiResponse<InventoryReceiptResponse>> rejectReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RejectReceiptRequest request
    ) {
        UUID userId = SecurityUtil.getCurrentUserId();
        String reason = request != null ? request.getReason() : null;
        InventoryReceiptResponse response = receiptService.rejectReceipt(userId, id, reason);
        return ResponseEntity.ok(ApiResponse.success("Từ chối phiếu thành công", response));
    }

    @GetMapping
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Lấy danh sách phiếu nhập/xuất kho phân trang theo kho")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryReceiptResponse>>> getReceipts(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) DocumentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        UUID userId = SecurityUtil.getCurrentUserId();
        PagedResponse<InventoryReceiptResponse> response = receiptService.getReceiptsByWarehouse(userId, warehouseId, type, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phiếu thành công", response));
    }


    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Xem chi tiết phiếu nhập/xuất kho")
    public ResponseEntity<ApiResponse<InventoryReceiptResponse>> getReceiptDetail(@PathVariable UUID id) {
        UUID userId = SecurityUtil.getCurrentUserId();
        InventoryReceiptResponse response = receiptService.getReceiptDetail(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết phiếu thành công", response));
    }

    @GetMapping("/export")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Xuất danh sách phiếu nhập/xuất kho ra file Excel/CSV")
    public ResponseEntity<byte[]> exportReceipts(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) DocumentType type
    ) {
        UUID userId = SecurityUtil.getCurrentUserId();
        byte[] csvData = receiptService.exportReceiptsToCsv(userId, warehouseId, type);
        String filename = "inventory_receipts_" + (type != null ? type.name().toLowerCase() : "all") + ".csv";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}


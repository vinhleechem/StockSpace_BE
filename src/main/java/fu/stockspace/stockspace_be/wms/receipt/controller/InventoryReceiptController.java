package fu.stockspace.stockspace_be.wms.receipt.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import fu.stockspace.stockspace_be.wms.receipt.dto.CreateInventoryReceiptRequest;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.CreateInventoryReceiptRequest;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
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
@PreAuthorize("hasAnyRole('TENANT', 'STAFF')")
public class InventoryReceiptController {

    private final InventoryReceiptService receiptService;
    private final SubscriptionService subscriptionService;

    private void checkSubscription() {
        UUID tenantId = TenantContextUtil.getCurrentTenantId();
        if (!subscriptionService.hasActiveSubscription(tenantId)) {
            throw new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED);
        }
    }

    @PostMapping
    @Operation(summary = "Tạo phiếu nhập/xuất kho mới ở trạng thái PENDING")
    public ResponseEntity<ApiResponse<InventoryReceiptResponse>> createReceipt(
            @Valid @RequestBody CreateInventoryReceiptRequest request
    ) {
        checkSubscription();
        UUID userId = SecurityUtil.getCurrentUserId();
        InventoryReceiptResponse response = receiptService.createReceipt(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Tạo phiếu nhập/xuất kho thành công", response));
    }

    @PatchMapping("/{id}/approve")
    @Operation(summary = "Duyệt phiếu nhập/xuất kho (cộng/trừ kho và ghi nhật ký giao dịch)")
    public ResponseEntity<ApiResponse<InventoryReceiptResponse>> approveReceipt(@PathVariable UUID id) {
        checkSubscription();
        UUID userId = SecurityUtil.getCurrentUserId();
        InventoryReceiptResponse response = receiptService.approveReceipt(userId, id);
        return ResponseEntity.ok(ApiResponse.success("Duyệt phiếu thành công, kho hàng đã cập nhật", response));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách phiếu nhập/xuất kho phân trang theo kho")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryReceiptResponse>>> getReceipts(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) DocumentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        checkSubscription();
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<InventoryReceiptResponse> response = receiptService.getReceiptsByWarehouse(warehouseId, type, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phiếu thành công", response));
    }


    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết phiếu nhập/xuất kho")
    public ResponseEntity<ApiResponse<InventoryReceiptResponse>> getReceiptDetail(@PathVariable UUID id) {
        checkSubscription();
        InventoryReceiptResponse response = receiptService.getReceiptDetail(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết phiếu thành công", response));
    }

    @GetMapping("/export")
    @Operation(summary = "Xuất danh sách phiếu nhập/xuất kho ra file Excel/CSV")
    public ResponseEntity<byte[]> exportReceipts(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) DocumentType type
    ) {
        checkSubscription();
        byte[] csvData = receiptService.exportReceiptsToCsv(warehouseId, type);
        String filename = "inventory_receipts_" + (type != null ? type.name().toLowerCase() : "all") + ".csv";

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }
}


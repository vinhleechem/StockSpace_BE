package fu.stockspace.stockspace_be.wms.stock.controller;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryTransactionResponse;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.PagedStockBatchResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.StockSummaryResponse;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "WMS Stock Batch Management", description = "Các API quản lý tồn kho (Stock Batch) dành cho Tenant")
@RestController
@RequestMapping("/api/tenant/inventory/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TENANT', 'STAFF')")
public class StockBatchController {

    private final StockBatchService stockBatchService;
    private final InventoryReceiptService inventoryReceiptService;

    private UUID getCurrentTenantId() {
        var user = SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new ForbiddenException(ErrorCode.UNAUTHENTICATED));
        return user.getTenant() != null ? user.getTenant().getId() : user.getId();
    }

    @GetMapping
    @Operation(summary = "Xem toàn bộ tồn kho trong kho đang thuê (phân trang theo warehouseId)")
    public ResponseEntity<ApiResponse<PagedStockBatchResponse>> getStockByWarehouse(
            @RequestParam UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID tenantId = getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);
        PagedStockBatchResponse response = stockBatchService.getStockByWarehouse(tenantId, warehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tồn kho thành công", response));
    }

    @GetMapping("/sku/{skuId}")
    @Operation(summary = "Xem tồn kho chi tiết theo SKU — tổng hợp tất cả vị trí lưu trữ")
    public ResponseEntity<ApiResponse<StockSummaryResponse>> getStockBySku(@PathVariable UUID skuId) {
        UUID tenantId = getCurrentTenantId();
        StockSummaryResponse response = stockBatchService.getStockSummaryBySku(tenantId, skuId);
        return ResponseEntity.ok(ApiResponse.success("Lấy tồn kho theo SKU thành công", response));
    }

    @GetMapping("/summary")
    @Operation(summary = "Tổng hợp tồn kho theo SKU — lấy theo skuId")
    public ResponseEntity<ApiResponse<StockSummaryResponse>> getStockSummary(@RequestParam UUID skuId) {
        UUID tenantId = getCurrentTenantId();
        StockSummaryResponse response = stockBatchService.getStockSummaryBySku(tenantId, skuId);
        return ResponseEntity.ok(ApiResponse.success("Tổng hợp tồn kho theo SKU thành công", response));
    }

    @GetMapping("/{batchId}/transactions")
    @Operation(summary = "Xem lịch sử biến động số lượng của một lô hàng cụ thể")
    public ResponseEntity<ApiResponse<Page<InventoryTransactionResponse>>> getTransactionsByBatch(
            @PathVariable UUID batchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<InventoryTransactionResponse> response = inventoryReceiptService.getTransactionsByBatch(batchId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử giao dịch thành công", response));
    }
}

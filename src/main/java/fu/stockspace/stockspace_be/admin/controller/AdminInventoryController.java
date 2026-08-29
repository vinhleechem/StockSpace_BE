package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.receipt.dto.InventoryReceiptResponse;
import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import fu.stockspace.stockspace_be.wms.receipt.service.InventoryReceiptService;
import fu.stockspace.stockspace_be.wms.stock.dto.InventoryAuditResponse;
import fu.stockspace.stockspace_be.wms.stock.dto.StockBatchResponse;
import fu.stockspace.stockspace_be.wms.stock.service.InventoryAuditService;
import fu.stockspace.stockspace_be.wms.stock.service.StockBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;




@Tag(name = "Admin — WMS Inventory Management", description = "Các API Admin xem dữ liệu WMS toàn hệ thống")
@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('ADMIN_INVENTORY_READ')")
public class AdminInventoryController {

    private final InventoryReceiptService inventoryReceiptService;
    private final InventoryAuditService inventoryAuditService;
    private final StockBatchService stockBatchService;





    @GetMapping("/receipts")
    @Operation(summary = "Xem tất cả phiếu nhập/xuất kho toàn hệ thống")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryReceiptResponse>>> getAllReceipts(
            @RequestParam UUID warehouseId,
            @RequestParam(required = false) DocumentType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<InventoryReceiptResponse> response = inventoryReceiptService.getReceiptsByWarehouse(warehouseId, type, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phiếu nhập/xuất thành công", response));
    }





    @GetMapping("/audits")
    @Operation(summary = "Xem tất cả phiếu kiểm kê toàn hệ thống")
    public ResponseEntity<ApiResponse<PagedResponse<InventoryAuditResponse>>> getAllAudits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PagedResponse<InventoryAuditResponse> response = inventoryAuditService.getAllAudits(pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phiếu kiểm kê thành công", response));
    }





    @GetMapping("/stock")
    @Operation(summary = "Tổng hợp tồn kho toàn hệ thống theo kho")
    public ResponseEntity<ApiResponse<PagedResponse<StockBatchResponse>>> getAllStock(
            @RequestParam UUID warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());



        var batchPage = stockBatchService.getAdminStockByWarehouse(warehouseId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tồn kho kho thành công", batchPage));
    }
}

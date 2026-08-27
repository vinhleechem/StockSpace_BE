package fu.stockspace.stockspace_be.wms.transfer.controller;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.CreateStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.entity.StockTransferStatus;
import fu.stockspace.stockspace_be.wms.transfer.service.StockTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Tenant — WMS Stock Transfer", description = "Chuyển tồn kho giữa các warehouse của cùng Tenant")
@RestController
@RequestMapping("/api/tenant/inventory/transfers")
@RequiredArgsConstructor
public class StockTransferController {

    private final StockTransferService transferService;

    @PostMapping
    @PreAuthorize("@rbac.hasPermission('INVENTORY_CREATE')")
    @Operation(summary = "Tạo yêu cầu chuyển kho ở trạng thái PENDING")
    public ResponseEntity<ApiResponse<StockTransferResponse>> createTransfer(
            @Valid @RequestBody CreateStockTransferRequest request) {
        StockTransferResponse response = transferService.createTransfer(
                SecurityUtil.getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Tạo yêu cầu chuyển kho thành công", response));
    }

    @GetMapping
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Lấy danh sách yêu cầu chuyển kho")
    public ResponseEntity<ApiResponse<PagedResponse<StockTransferResponse>>> getTransfers(
            @RequestParam(required = false) UUID sourceWarehouseId,
            @RequestParam(required = false) UUID destinationWarehouseId,
            @RequestParam(required = false) StockTransferStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        PagedResponse<StockTransferResponse> response = transferService.getTransfers(
                SecurityUtil.getCurrentUserId(), sourceWarehouseId, destinationWarehouseId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chuyển kho thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Lấy chi tiết yêu cầu chuyển kho")
    public ResponseEntity<ApiResponse<StockTransferResponse>> getTransfer(@PathVariable UUID id) {
        StockTransferResponse response = transferService.getTransfer(
                SecurityUtil.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết chuyển kho thành công", response));
    }

    @PatchMapping("/{id}/approve-dispatch")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Duyệt xuất kho và chuyển trạng thái sang IN_TRANSIT")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveDispatch(@PathVariable UUID id) {
        StockTransferResponse response = transferService.approveDispatch(
                SecurityUtil.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Duyệt xuất kho chuyển tiếp thành công", response));
    }
}

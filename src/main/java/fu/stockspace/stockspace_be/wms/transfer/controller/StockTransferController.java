package fu.stockspace.stockspace_be.wms.transfer.controller;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.AssignStockTransferDestinationStaffRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.CreateStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.ReceiveStockTransferRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferDecisionRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferEventResponse;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferPickRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferReconcileRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferReturnRequest;
import fu.stockspace.stockspace_be.wms.transfer.dto.StockTransferRetryRequest;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.List;

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

    @PatchMapping("/{id}/destination-staff")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Giao hoặc đổi staff nhận hàng tại kho đích hiện tại")
    public ResponseEntity<ApiResponse<StockTransferResponse>> assignDestinationStaff(
            @PathVariable UUID id,
            @Valid @RequestBody AssignStockTransferDestinationStaffRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.assignDestinationStaff(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Giao staff nhận hàng thành công", response));
    }

    @PatchMapping("/{id}/approve-dispatch")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Duyệt xuất kho và chuyển trạng thái sang IN_TRANSIT")
    public ResponseEntity<ApiResponse<StockTransferResponse>> approveDispatch(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.approveDispatch(
                SecurityUtil.getCurrentUserId(), id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Duyệt xuất kho chuyển tiếp thành công", response));
    }

    @GetMapping("/{id}/timeline")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    public ResponseEntity<ApiResponse<List<StockTransferEventResponse>>> getTransferTimeline(
            @PathVariable UUID id) {
        List<StockTransferEventResponse> response = transferService.getTransferTimeline(
                SecurityUtil.getCurrentUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("Transfer timeline loaded", response));
    }

    @PatchMapping("/{id}/allocate")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> allocateTransfer(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.allocateTransfer(
                SecurityUtil.getCurrentUserId(), id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Allocation transfer created", response));
    }

    @PostMapping("/{id}/pick")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> pickTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferPickRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.pickTransfer(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Transfer picked successfully", response));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Nhận hàng tại kho đích và hoàn tất chuyển kho")
    public ResponseEntity<ApiResponse<StockTransferResponse>> receiveTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody ReceiveStockTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.receiveTransfer(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Nhận chuyển kho thành công", response));
    }

    @PatchMapping("/{id}/arrive")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> arriveTransfer(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.arriveTransfer(
                SecurityUtil.getCurrentUserId(), id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận hàng đã đến kho đích", response));
    }

    @PatchMapping("/{id}/reject-receipt")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferDecisionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.rejectReceipt(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận kho đích từ chối nhận", response));
    }

    @PatchMapping("/{id}/close-short")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> closeShortReceipt(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferDecisionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.closeShortReceipt(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Đã đóng biên bản nhận thiếu", response));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> retryTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferRetryRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.retryTransfer(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo chặng chuyển kho tiếp theo", response));
    }

    @PatchMapping("/{id}/retry/dispatch")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> dispatchRetry(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.dispatchRetry(
                SecurityUtil.getCurrentUserId(), id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Đã xuất lại chặng chuyển kho", response));
    }

    @PostMapping("/{id}/return/request")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> requestReturn(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferDecisionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.requestReturn(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Đã tạo yêu cầu quay đầu về kho nguồn", response));
    }

    @PatchMapping("/{id}/return/dispatch")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> dispatchReturn(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.dispatchReturn(
                SecurityUtil.getCurrentUserId(), id, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Đã xuất chặng hàng quay đầu", response));
    }

    @PostMapping("/{id}/return/receive")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> receiveReturn(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferReturnRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.receiveReturn(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Đã nhập hàng quay đầu về kho nguồn", response));
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    public ResponseEntity<ApiResponse<StockTransferResponse>> reconcileTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferReconcileRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        StockTransferResponse response = transferService.reconcileTransfer(
                SecurityUtil.getCurrentUserId(), id, request, idempotencyKey);
        return ResponseEntity.ok(ApiResponse.success("Transfer reconciled successfully", response));
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Từ chối yêu cầu chuyển kho đang PENDING")
    public ResponseEntity<ApiResponse<StockTransferResponse>> rejectTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferDecisionRequest request) {
        StockTransferResponse response = transferService.rejectTransfer(
                SecurityUtil.getCurrentUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Từ chối yêu cầu chuyển kho thành công", response));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_UPDATE')")
    @Operation(summary = "Hủy yêu cầu chuyển kho đang PENDING")
    public ResponseEntity<ApiResponse<StockTransferResponse>> cancelTransfer(
            @PathVariable UUID id,
            @Valid @RequestBody StockTransferDecisionRequest request) {
        StockTransferResponse response = transferService.cancelTransfer(
                SecurityUtil.getCurrentUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("Hủy yêu cầu chuyển kho thành công", response));
    }
}

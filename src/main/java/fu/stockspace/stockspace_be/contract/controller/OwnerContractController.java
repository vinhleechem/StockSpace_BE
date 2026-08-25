package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.CreateRentalContractRequest;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import fu.stockspace.stockspace_be.warehouse.dto.BulkLayoutSaveRequest;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Owner — Rental Contract", description = "Owner-side rental contract draft APIs")
@RestController
@RequestMapping("/api/owner/contracts")
@RequiredArgsConstructor
public class OwnerContractController {

    private final ContractService contractService;

    @PostMapping("/preview")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "Preview a rental contract draft without saving")
    public ResponseEntity<ApiResponse<RentalContractResponse>> preview(
            @Valid @RequestBody CreateRentalContractRequest request) {
        RentalContractResponse response = contractService.previewOwnerDraft(getCurrentUserId(), request);
        return ResponseEntity.ok(ApiResponse.success("Rental contract preview generated", response));
    }

    @PostMapping
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "Create a direct rental contract draft")
    public ResponseEntity<ApiResponse<RentalContractResponse>> create(
            @Valid @RequestBody CreateRentalContractRequest request) {
        RentalContractResponse response = contractService.createOwnerDraft(getCurrentUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rental contract draft created", response));
    }

    @DeleteMapping("/{contractId}")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "Delete a rental contract draft")
    public ResponseEntity<ApiResponse<Void>> deleteDraft(@PathVariable UUID contractId) {
        contractService.deleteOwnerDraft(getCurrentUserId(), contractId);
        return ResponseEntity.ok(ApiResponse.success("Rental contract draft deleted", null));
    }

    @GetMapping("/{contractId}/layout")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "View the layout proposal of an owned contract")
    public ResponseEntity<ApiResponse<WarehouseLayoutResponse>> getContractLayout(
            @PathVariable UUID contractId) {
        WarehouseLayoutResponse response = contractService
                .getOwnerContractLayout(getCurrentUserId(), contractId);
        return ResponseEntity.ok(ApiResponse.success("Contract layout loaded", response));
    }

    @PutMapping("/{contractId}/layout")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "Update the layout proposal of an owned contract")
    public ResponseEntity<ApiResponse<WarehouseLayoutResponse>> updateContractLayout(
            @PathVariable UUID contractId,
            @Valid @RequestBody BulkLayoutSaveRequest request) {
        WarehouseLayoutResponse response = contractService
                .updateOwnerContractLayout(getCurrentUserId(), contractId, request);
        return ResponseEntity.ok(ApiResponse.success("Contract layout updated", response));
    }

    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}

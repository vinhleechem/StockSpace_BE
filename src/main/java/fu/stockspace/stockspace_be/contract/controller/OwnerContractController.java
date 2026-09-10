package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.CreateContractRenewalRequest;
import fu.stockspace.stockspace_be.contract.dto.CreateRentalContractRequest;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.dto.UpdateRentalContractRequest;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import fu.stockspace.stockspace_be.warehouse.dto.BulkLayoutSaveRequest;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid participant, state, pricing, or layout dimensions",
                content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = {
                        @ExampleObject(name = "Invalid role", value = "{\"success\":false,\"code\":\"INVALID_ROLE\",\"message\":\"The supplied account does not have the TENANT role\"}"),
                        @ExampleObject(name = "Invalid status", value = "{\"success\":false,\"code\":\"INVALID_CONTRACT_STATUS\",\"message\":\"Only DRAFT or CHANGES_REQUESTED contracts can be submitted\"}"),
                        @ExampleObject(name = "Invalid dimensions", value = "{\"success\":false,\"code\":\"INVALID_LEASE_DIMENSIONS\",\"message\":\"Leased dimensions cannot exceed the warehouse default layout\"}"),
                        @ExampleObject(name = "Renewal is not allowed", value = "{\"success\":false,\"code\":\"CONTRACT_RENEWAL_NOT_ALLOWED\",\"message\":\"The active source contract is not eligible for renewal\"}"),
                        @ExampleObject(name = "Renewal deadline passed", value = "{\"success\":false,\"code\":\"CONTRACT_RENEWAL_DEADLINE_PASSED\",\"message\":\"The renewal deadline has passed\"}"),
                        @ExampleObject(name = "Paper contract required", value = "{\"success\":false,\"code\":\"PAPER_CONTRACT_REQUIRED\",\"message\":\"At least one valid paper contract file is required before submission\"}")
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Warehouse or contract is not owned by the caller",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"WAREHOUSE_NOT_OWNED\",\"message\":\"You are not the warehouse owner\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Tenant or contract not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = {
                        @ExampleObject(name = "Tenant not found", value = "{\"success\":false,\"code\":\"TENANT_NOT_FOUND\",\"message\":\"Active tenant account was not found for the supplied email\"}"),
                        @ExampleObject(name = "Contract not found", value = "{\"success\":false,\"code\":\"CONTRACT_NOT_FOUND\",\"message\":\"Rental contract not found\"}")
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Contract dates, area, or renewal source conflict",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = {
                                @ExampleObject(name = "Date overlap", value = "{\"success\":false,\"code\":\"CONTRACT_DATE_OVERLAP\",\"message\":\"The tenant already has an overlapping contract for this warehouse\"}"),
                                @ExampleObject(name = "Renewal already exists", value = "{\"success\":false,\"code\":\"CONTRACT_RENEWAL_ALREADY_EXISTS\",\"message\":\"The source contract already has a renewal request in progress\"}"),
                                @ExampleObject(name = "Pricing changed", value = "{\"success\":false,\"code\":\"CONTRACT_RENEWAL_PRICING_CHANGED\",\"message\":\"The warehouse pricing type changed and this renewal is no longer valid\"}"),
                                @ExampleObject(name = "Area unavailable", value = "{\"success\":false,\"code\":\"WAREHOUSE_AREA_UNAVAILABLE\",\"message\":\"The requested leased area is not available for the selected dates\"}")
                        }))
})
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

    @PostMapping("/{sourceContractId}/renewal-draft")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "Create a rental contract renewal draft")
    public ResponseEntity<ApiResponse<RentalContractResponse>> createRenewalDraft(
            @PathVariable UUID sourceContractId,
            @Valid @RequestBody CreateContractRenewalRequest request) {
        RentalContractResponse response = contractService.createRenewalDraft(
                getCurrentUserId(), sourceContractId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Rental contract renewal draft created", response));
    }

    @PutMapping("/{contractId}")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "Update a direct rental contract draft")
    public ResponseEntity<ApiResponse<RentalContractResponse>> update(
            @PathVariable UUID contractId,
            @Valid @RequestBody UpdateRentalContractRequest request) {
        RentalContractResponse response = contractService
                .updateOwnerDraft(getCurrentUserId(), contractId, request);
        return ResponseEntity.ok(ApiResponse.success("Rental contract draft updated", response));
    }

    @PostMapping("/{contractId}/submit")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_OWNER_MANAGE')")
    @Operation(summary = "Submit a direct rental contract to the tenant")
    public ResponseEntity<ApiResponse<RentalContractResponse>> submit(
            @PathVariable UUID contractId) {
        RentalContractResponse response = contractService
                .submitOwnerContract(getCurrentUserId(), contractId);
        return ResponseEntity.ok(ApiResponse.success("Rental contract submitted to tenant", response));
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

package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.dto.TenantContractDecisionRequest;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Tenant — Contract Layout", description = "Read-only contract layout proposal APIs")
@RestController
@RequestMapping("/api/tenant/contracts")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('CONTRACT_READ')")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Contract is not in a tenant-review state",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"INVALID_CONTRACT_STATUS\",\"message\":\"Contract must be in PENDING_TENANT_CONFIRM status for this action\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller is not this contract's tenant",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"You cannot access this contract\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Contract not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"CONTRACT_NOT_FOUND\",\"message\":\"Rental contract not found\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Confirmation would overlap an existing contract",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"CONTRACT_DATE_OVERLAP\",\"message\":\"The tenant already has an overlapping contract for this warehouse\"}")))
})
public class TenantContractController {

    private final ContractService contractService;

    @GetMapping("/{contractId}/layout")
    @Operation(summary = "View the layout proposal of a tenant contract")
    public ResponseEntity<ApiResponse<WarehouseLayoutResponse>> getContractLayout(
            @PathVariable UUID contractId) {
        WarehouseLayoutResponse response = contractService
                .getTenantContractLayout(getCurrentUserId(), contractId);
        return ResponseEntity.ok(ApiResponse.success("Contract layout loaded", response));
    }

    @PostMapping("/{contractId}/confirm")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_TENANT_MANAGE')")
    @Operation(summary = "Confirm a submitted direct rental contract")
    public ResponseEntity<ApiResponse<RentalContractResponse>> confirmContract(
            @PathVariable UUID contractId) {
        RentalContractResponse response = contractService
                .confirmDirectContract(getCurrentUserId(), contractId);
        return ResponseEntity.ok(ApiResponse.success("Contract confirmed", response));
    }

    @PostMapping("/{contractId}/request-changes")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_TENANT_MANAGE')")
    @Operation(summary = "Request changes to a submitted direct rental contract")
    public ResponseEntity<ApiResponse<RentalContractResponse>> requestChanges(
            @PathVariable UUID contractId,
            @Valid @RequestBody TenantContractDecisionRequest request) {
        RentalContractResponse response = contractService
                .requestDirectContractChanges(getCurrentUserId(), contractId, request);
        return ResponseEntity.ok(ApiResponse.success("Contract changes requested", response));
    }

    @PostMapping("/{contractId}/reject")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_TENANT_MANAGE')")
    @Operation(summary = "Reject a submitted direct rental contract")
    public ResponseEntity<ApiResponse<RentalContractResponse>> rejectContract(
            @PathVariable UUID contractId,
            @Valid @RequestBody TenantContractDecisionRequest request) {
        RentalContractResponse response = contractService
                .rejectDirectContract(getCurrentUserId(), contractId, request);
        return ResponseEntity.ok(ApiResponse.success("Contract rejected", response));
    }

    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(User::getId)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}

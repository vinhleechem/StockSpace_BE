package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.service.ContractService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Contract", description = "Read-only access to the authenticated user's rental contracts")
@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Viewer is not a contract participant",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"You cannot access this contract\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Contract not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"CONTRACT_NOT_FOUND\",\"message\":\"Rental contract not found\"}")))
})
public class ContractController {

    private final ContractService contractService;

    @GetMapping
    @PreAuthorize("@rbac.hasPermission('CONTRACT_READ')")
    @Operation(summary = "List rental contracts for the authenticated owner or tenant")
    public ResponseEntity<ApiResponse<PagedResponse<RentalContractResponse>>> getMyContracts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = getCurrentUser();
        boolean isOwner = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ROLE_OWNER"));
        Page<RentalContractResponse> result = isOwner
                ? contractService.getMyContractsAsOwner(user.getId(), page, size)
                : contractService.getMyContractsAsTenant(user.getId(), page, size);
        return ResponseEntity.ok(ApiResponse.success(
                "Rental contracts loaded", PagedResponse.fromPage(result)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbac.hasPermission('CONTRACT_READ')")
    @Operation(summary = "Get a rental contract owned by the authenticated owner or tenant")
    public ResponseEntity<ApiResponse<RentalContractResponse>> getById(@PathVariable UUID id) {
        RentalContractResponse response = contractService.getContractById(id, getCurrentUser().getId());
        return ResponseEntity.ok(ApiResponse.success("Rental contract loaded", response));
    }

    private User getCurrentUser() {
        return SecurityUtil.getCurrentUser()
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}

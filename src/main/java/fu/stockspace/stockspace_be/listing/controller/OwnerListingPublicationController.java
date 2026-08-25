package fu.stockspace.stockspace_be.listing.controller;

import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.listing.dto.ListingOrderResponse;
import fu.stockspace.stockspace_be.listing.dto.PurchaseListingPackageRequest;
import fu.stockspace.stockspace_be.listing.service.ListingOrderService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/warehouses/{warehouseId}/publications")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('WAREHOUSE_UPDATE')")
@Tag(name = "Owner - Warehouse Publications")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Publication package cannot be purchased",
                content = @Content(schema = @Schema(implementation = ApiResponse.class), examples = {
                        @ExampleObject(name = "Inactive package", value = "{\"success\":false,\"code\":\"LISTING_PACKAGE_INACTIVE\",\"message\":\"Listing package is inactive\"}"),
                        @ExampleObject(name = "Insufficient balance", value = "{\"success\":false,\"code\":\"INSUFFICIENT_BALANCE\",\"message\":\"Wallet balance is insufficient\"}")
                })),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Warehouse is not owned by the caller",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"WAREHOUSE_NOT_OWNED\",\"message\":\"You are not the warehouse owner\"}"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Warehouse or package not found",
                content = @Content(schema = @Schema(implementation = ApiResponse.class),
                        examples = @ExampleObject(value = "{\"success\":false,\"code\":\"PACKAGE_NOT_FOUND\",\"message\":\"Listing package not found\"}")))
})
public class OwnerListingPublicationController {

    private final ListingOrderService listingOrderService;

    @PostMapping
    @Operation(summary = "Purchase or renew a warehouse listing package")
    public ResponseEntity<ApiResponse<ListingOrderResponse>> purchase(
            @PathVariable UUID warehouseId,
            @Valid @RequestBody PurchaseListingPackageRequest request) {
        ListingOrderResponse response = listingOrderService.purchaseOrRenew(
                getCurrentUserId(), warehouseId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Warehouse publication package purchased successfully", response));
    }

    @GetMapping
    @Operation(summary = "View warehouse publication purchase history")
    public ResponseEntity<ApiResponse<List<ListingOrderResponse>>> getHistory(
            @PathVariable UUID warehouseId) {
        List<ListingOrderResponse> response = listingOrderService.getPublicationHistory(
                getCurrentUserId(), warehouseId);
        return ResponseEntity.ok(ApiResponse.success("Warehouse publication history retrieved successfully", response));
    }

    private UUID getCurrentUserId() {
        return SecurityUtil.getCurrentUser()
                .map(user -> user.getId())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.UNAUTHENTICATED));
    }
}

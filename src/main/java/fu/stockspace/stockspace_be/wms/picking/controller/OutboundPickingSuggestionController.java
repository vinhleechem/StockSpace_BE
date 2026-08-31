package fu.stockspace.stockspace_be.wms.picking.controller;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wms.picking.OutboundPickingInputItem;
import fu.stockspace.stockspace_be.wms.picking.OutboundPickingSuggestionService;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionItemRequest;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionRequest;
import fu.stockspace.stockspace_be.wms.picking.dto.OutboundPickingSuggestionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Tenant — WMS Picking Suggestions",
        description = "Read-only FIFO pick-list suggestions for outbound stock")
@RestController
@RequestMapping("/api/tenant/inventory/picking")
@RequiredArgsConstructor
public class OutboundPickingSuggestionController {

    private final OutboundPickingSuggestionService suggestionService;

    @PostMapping("/suggestions")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Preview FIFO pick-list suggestions for outbound stock")
    public ResponseEntity<ApiResponse<OutboundPickingSuggestionResponse>> suggest(
            @Valid @RequestBody OutboundPickingSuggestionRequest request) {
        UUID tenantId = TenantContextUtil.getCurrentTenantId();
        OutboundPickingSuggestionResponse response = suggestionService.suggest(
                tenantId,
                getCurrentStaffIdIfApplicable(),
                request.getWarehouseId(),
                request.getItems().stream()
                        .map(this::toInputItem)
                        .toList());
        return ResponseEntity.ok(ApiResponse.success(
                "Outbound pick-list suggestions calculated successfully", response));
    }

    private OutboundPickingInputItem toInputItem(OutboundPickingSuggestionItemRequest request) {
        return new OutboundPickingInputItem(request.getSkuId(), request.getQuantity());
    }

    private UUID getCurrentStaffIdIfApplicable() {
        return SecurityUtil.getCurrentUser()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName())))
                .map(user -> user.getId())
                .orElse(null);
    }
}

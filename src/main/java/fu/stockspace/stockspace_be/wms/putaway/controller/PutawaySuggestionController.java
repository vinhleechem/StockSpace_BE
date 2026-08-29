package fu.stockspace.stockspace_be.wms.putaway.controller;

import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.util.SecurityUtil;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.wms.putaway.PutawayInputItem;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionResult;
import fu.stockspace.stockspace_be.wms.putaway.PutawaySuggestionService;
import fu.stockspace.stockspace_be.wms.putaway.dto.PutawaySuggestionItemRequest;
import fu.stockspace.stockspace_be.wms.putaway.dto.PutawaySuggestionRequest;
import fu.stockspace.stockspace_be.wms.putaway.dto.PutawaySuggestionResponse;
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

@Tag(name = "Tenant — WMS Put-away Suggestions",
        description = "Read-only capacity-aware bin recommendations for inbound and transfer receiving")
@RestController
@RequestMapping("/api/tenant/inventory/putaway")
@RequiredArgsConstructor
public class PutawaySuggestionController {

    private final PutawaySuggestionService suggestionService;

    @PostMapping("/suggestions")
    @PreAuthorize("@rbac.hasPermission('INVENTORY_READ')")
    @Operation(summary = "Suggest suitable bins for inbound or transfer receiving")
    public ResponseEntity<ApiResponse<PutawaySuggestionResponse>> suggest(
            @Valid @RequestBody PutawaySuggestionRequest request) {
        UUID tenantId = TenantContextUtil.getCurrentTenantId();
        PutawaySuggestionResult result = suggestionService.suggest(
                tenantId,
                getCurrentStaffIdIfApplicable(),
                request.getWarehouseId(),
                request.getItems().stream()
                        .map(this::toInputItem)
                        .toList());
        PutawaySuggestionResponse response = new PutawaySuggestionResponse(
                result.warehouseId(), result.layoutId(), request.getContext(), result.items());
        return ResponseEntity.ok(ApiResponse.success("Put-away suggestions calculated successfully", response));
    }

    private PutawayInputItem toInputItem(PutawaySuggestionItemRequest request) {
        return new PutawayInputItem(request.getSkuId(), request.getQuantity());
    }

    private UUID getCurrentStaffIdIfApplicable() {
        return SecurityUtil.getCurrentUser()
                .filter(user -> user.getRoles() != null && user.getRoles().stream()
                        .anyMatch(role -> RoleType.ROLE_STAFF.name().equals(role.getName())))
                .map(user -> user.getId())
                .orElse(null);
    }
}

package fu.stockspace.stockspace_be.admin.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.listing.dto.CreateListingPackageRequest;
import fu.stockspace.stockspace_be.listing.dto.ListingPackageResponse;
import fu.stockspace.stockspace_be.listing.dto.UpdateListingPackageRequest;
import fu.stockspace.stockspace_be.listing.service.ListingPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/listing-packages")
@RequiredArgsConstructor
@PreAuthorize("@rbac.hasPermission('ADMIN_PACKAGE_MANAGE')")
@Tag(name = "Admin - Warehouse Listing Packages")
public class AdminListingPackageController {

    private final ListingPackageService listingPackageService;

    @GetMapping
    @Operation(summary = "List all warehouse listing packages")
    public ResponseEntity<ApiResponse<List<ListingPackageResponse>>> getPackages() {
        return ResponseEntity.ok(ApiResponse.success(
                "Listing packages retrieved successfully",
                listingPackageService.getAdminPackages()
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a warehouse listing package")
    public ResponseEntity<ApiResponse<ListingPackageResponse>> getPackage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Listing package retrieved successfully",
                listingPackageService.getAdminPackageById(id)
        ));
    }

    @PostMapping
    @Operation(summary = "Create a warehouse listing package")
    public ResponseEntity<ApiResponse<ListingPackageResponse>> createPackage(
            @Valid @RequestBody CreateListingPackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Listing package created successfully",
                listingPackageService.createPackage(request)
        ));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a warehouse listing package")
    public ResponseEntity<ApiResponse<ListingPackageResponse>> updatePackage(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateListingPackageRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Listing package updated successfully",
                listingPackageService.updatePackage(id, request)
        ));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a warehouse listing package")
    public ResponseEntity<ApiResponse<Void>> deletePackage(@PathVariable UUID id) {
        listingPackageService.deletePackage(id);
        return ResponseEntity.ok(ApiResponse.success("Listing package deleted successfully", null));
    }

    @PatchMapping("/{id}/activate")
    @Operation(summary = "Activate a warehouse listing package")
    public ResponseEntity<ApiResponse<ListingPackageResponse>> activatePackage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Listing package activated successfully",
                listingPackageService.activatePackage(id)
        ));
    }

    @PatchMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a warehouse listing package")
    public ResponseEntity<ApiResponse<ListingPackageResponse>> deactivatePackage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Listing package deactivated successfully",
                listingPackageService.deactivatePackage(id)
        ));
    }
}

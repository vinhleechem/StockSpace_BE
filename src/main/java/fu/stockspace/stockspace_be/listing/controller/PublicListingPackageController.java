package fu.stockspace.stockspace_be.listing.controller;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.listing.dto.ListingPackageResponse;
import fu.stockspace.stockspace_be.listing.service.ListingPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/listing-packages")
@RequiredArgsConstructor
@Tag(name = "Public - Warehouse Listing Packages")
public class PublicListingPackageController {

    private final ListingPackageService listingPackageService;

    @GetMapping
    @Operation(summary = "List active warehouse listing packages")
    public ResponseEntity<ApiResponse<List<ListingPackageResponse>>> getPackages() {
        return ResponseEntity.ok(ApiResponse.success(
                "Listing packages retrieved successfully",
                listingPackageService.getPublicPackages()
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active warehouse listing package")
    public ResponseEntity<ApiResponse<ListingPackageResponse>> getPackage(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Listing package retrieved successfully",
                listingPackageService.getPublicPackageById(id)
        ));
    }
}

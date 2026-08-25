package fu.stockspace.stockspace_be.listing.service;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.listing.dto.CreateListingPackageRequest;
import fu.stockspace.stockspace_be.listing.dto.UpdateListingPackageRequest;
import fu.stockspace.stockspace_be.listing.entity.ListingPackage;
import fu.stockspace.stockspace_be.listing.repository.ListingPackageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingPackageServiceTest {

    @Mock
    private ListingPackageRepository listingPackageRepository;

    @InjectMocks
    private ListingPackageService listingPackageService;

    @Test
    void getPublicPackagesReturnsOnlyRepositoryProvidedActivePackages() {
        ListingPackage package10 = packageEntity(10, true, false);
        when(listingPackageRepository.findAllByIsActiveTrueAndIsDeletedFalseOrderByDurationDaysAsc())
                .thenReturn(List.of(package10));

        var response = listingPackageService.getPublicPackages();

        assertEquals(1, response.size());
        assertEquals(10, response.get(0).getDurationDays());
        assertEquals(new BigDecimal("50000.00"), response.get(0).getPrice());
        verify(listingPackageRepository).findAllByIsActiveTrueAndIsDeletedFalseOrderByDurationDaysAsc();
    }

    @Test
    void getPublicPackageByIdRejectsInactivePackage() {
        UUID id = UUID.randomUUID();
        when(listingPackageRepository.findById(id)).thenReturn(Optional.of(packageEntity(10, false, false)));

        assertThrows(ResourceNotFoundException.class, () -> listingPackageService.getPublicPackageById(id));
    }

    @Test
    void createPackageRejectsUnsupportedDuration() {
        CreateListingPackageRequest request = CreateListingPackageRequest.builder()
                .name("Listing Package - 20 Days")
                .durationDays(20)
                .price(new BigDecimal("80000.00"))
                .build();

        assertThrows(BadRequestException.class, () -> listingPackageService.createPackage(request));
        verify(listingPackageRepository, never()).save(any());
    }

    @Test
    void createPackageRejectsDuplicateDuration() {
        CreateListingPackageRequest request = CreateListingPackageRequest.builder()
                .name("Another 10-day package")
                .durationDays(10)
                .price(new BigDecimal("55000.00"))
                .build();
        when(listingPackageRepository.findByDurationDays(10))
                .thenReturn(Optional.of(packageEntity(10, false, true)));

        assertThrows(ResourceConflictException.class, () -> listingPackageService.createPackage(request));
        verify(listingPackageRepository, never()).save(any());
    }

    @Test
    void updatePackageRejectsDuplicateDuration() {
        UUID id = UUID.randomUUID();
        when(listingPackageRepository.findById(id)).thenReturn(Optional.of(packageEntity(id, 10, true, false)));
        when(listingPackageRepository.existsByDurationDaysAndIdNot(15, id)).thenReturn(true);

        UpdateListingPackageRequest request = UpdateListingPackageRequest.builder()
                .durationDays(15)
                .build();

        assertThrows(ResourceConflictException.class, () -> listingPackageService.updatePackage(id, request));
        verify(listingPackageRepository, never()).save(any());
    }

    @Test
    void activatePackageRestoresSoftDeletedPackage() {
        UUID id = UUID.randomUUID();
        ListingPackage entity = packageEntity(id, 10, false, true);
        when(listingPackageRepository.findById(id)).thenReturn(Optional.of(entity));
        when(listingPackageRepository.save(entity)).thenReturn(entity);

        listingPackageService.activatePackage(id);

        assertEquals(true, entity.isActive());
        assertEquals(false, entity.isDeleted());
        verify(listingPackageRepository).save(entity);
    }

    private ListingPackage packageEntity(int durationDays, boolean active, boolean deleted) {
        return packageEntity(UUID.randomUUID(), durationDays, active, deleted);
    }

    private ListingPackage packageEntity(UUID id, int durationDays, boolean active, boolean deleted) {
        return ListingPackage.builder()
                .id(id)
                .name("Listing Package - " + durationDays + " Days")
                .durationDays(durationDays)
                .price(new BigDecimal(durationDays == 10 ? "50000.00" : "70000.00"))
                .isActive(active)
                .isDeleted(deleted)
                .build();
    }
}

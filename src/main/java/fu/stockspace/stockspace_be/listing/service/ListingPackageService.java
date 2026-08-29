package fu.stockspace.stockspace_be.listing.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.listing.dto.CreateListingPackageRequest;
import fu.stockspace.stockspace_be.listing.dto.ListingPackageResponse;
import fu.stockspace.stockspace_be.listing.dto.UpdateListingPackageRequest;
import fu.stockspace.stockspace_be.listing.entity.ListingPackage;
import fu.stockspace.stockspace_be.listing.repository.ListingPackageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListingPackageService {

    private static final Set<Integer> SUPPORTED_DURATIONS = Set.of(10, 15, 30);

    private final ListingPackageRepository listingPackageRepository;

    @Transactional(readOnly = true)
    public List<ListingPackageResponse> getPublicPackages() {
        return listingPackageRepository.findAllByIsActiveTrueAndIsDeletedFalseOrderByDurationDaysAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ListingPackageResponse getPublicPackageById(UUID id) {
        ListingPackage listingPackage = listingPackageRepository.findById(id)
                .filter(packageEntity -> packageEntity.isActive() && !packageEntity.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        return mapToResponse(listingPackage);
    }

    @Transactional(readOnly = true)
    public List<ListingPackageResponse> getAdminPackages() {
        return listingPackageRepository.findAllByOrderByDurationDaysAsc()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ListingPackageResponse getAdminPackageById(UUID id) {
        return mapToResponse(findPackage(id));
    }

    @Transactional
    public ListingPackageResponse createPackage(CreateListingPackageRequest request) {
        validateDuration(request.getDurationDays());
        ensureDurationIsUnique(request.getDurationDays(), null);

        ListingPackage listingPackage = ListingPackage.builder()
                .name(request.getName().trim())
                .durationDays(request.getDurationDays())
                .price(request.getPrice())
                .isActive(true)
                .isDeleted(false)
                .build();

        return mapToResponse(listingPackageRepository.save(listingPackage));
    }

    @Transactional
    public ListingPackageResponse updatePackage(UUID id, UpdateListingPackageRequest request) {
        ListingPackage listingPackage = findPackage(id);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new BadRequestException("Listing package name must not be blank");
            }
            listingPackage.setName(name);
        }
        if (request.getDurationDays() != null) {
            validateDuration(request.getDurationDays());
            ensureDurationIsUnique(request.getDurationDays(), id);
            listingPackage.setDurationDays(request.getDurationDays());
        }
        if (request.getPrice() != null) {
            listingPackage.setPrice(request.getPrice());
        }

        return mapToResponse(listingPackageRepository.save(listingPackage));
    }

    @Transactional
    public void deletePackage(UUID id) {
        ListingPackage listingPackage = findPackage(id);
        listingPackage.setActive(false);
        listingPackage.setDeleted(true);
        listingPackageRepository.save(listingPackage);
    }

    @Transactional
    public ListingPackageResponse activatePackage(UUID id) {
        ListingPackage listingPackage = findPackage(id);
        listingPackage.setDeleted(false);
        listingPackage.setActive(true);
        return mapToResponse(listingPackageRepository.save(listingPackage));
    }

    @Transactional
    public ListingPackageResponse deactivatePackage(UUID id) {
        ListingPackage listingPackage = findPackage(id);
        listingPackage.setActive(false);
        return mapToResponse(listingPackageRepository.save(listingPackage));
    }

    private ListingPackage findPackage(UUID id) {
        return listingPackageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
    }

    private void validateDuration(Integer durationDays) {
        if (!SUPPORTED_DURATIONS.contains(durationDays)) {
            throw new BadRequestException("Listing package duration must be one of 10, 15, or 30 days");
        }
    }

    private void ensureDurationIsUnique(Integer durationDays, UUID currentId) {
        boolean exists = currentId == null
                ? listingPackageRepository.findByDurationDays(durationDays).isPresent()
                : listingPackageRepository.existsByDurationDaysAndIdNot(durationDays, currentId);
        if (exists) {
            throw new ResourceConflictException("A listing package with this duration already exists");
        }
    }

    public ListingPackageResponse mapToResponse(ListingPackage listingPackage) {
        return ListingPackageResponse.builder()
                .id(listingPackage.getId())
                .name(listingPackage.getName())
                .durationDays(listingPackage.getDurationDays())
                .price(listingPackage.getPrice())
                .isActive(listingPackage.isActive())
                .build();
    }
}

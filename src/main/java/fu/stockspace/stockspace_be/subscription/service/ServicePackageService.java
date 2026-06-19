package fu.stockspace.stockspace_be.subscription.service;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.subscription.dto.CreatePackageRequest;
import fu.stockspace.stockspace_be.subscription.dto.ServicePackageResponse;
import fu.stockspace.stockspace_be.subscription.dto.UpdatePackageRequest;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.repository.ServicePackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicePackageService {
    private final ServicePackageRepository packageRepository;
    @Transactional(readOnly = true)
    public List<ServicePackageResponse> getAllPackages() {
        return packageRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }
    @Transactional(readOnly = true)
    public ServicePackageResponse getPackageById(java.util.UUID id) {
        ServicePackage servicePackage = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        return mapToResponse(servicePackage);
    }
    @Transactional
    public ServicePackageResponse createPackage(CreatePackageRequest request) {
        if (packageRepository.findByName(request.getName()).isPresent()) {
            throw new ResourceConflictException("Gói dịch vụ có tên này đã tồn tại");
        }
        ServicePackage servicePackage = ServicePackage.builder()
                .name(request.getName())
                .features(request.getFeatures())
                .price(request.getPrice())
                .durationDays(request.getDurationDays())
                .isActive(true)
                .build();
        servicePackage = packageRepository.save(servicePackage);
        log.info("Subscription Service: Created service package: {}", servicePackage.getName());
        return mapToResponse(servicePackage);
    }
    @Transactional
    public ServicePackageResponse updatePackage(java.util.UUID id, UpdatePackageRequest request) {
        ServicePackage servicePackage = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        if (request.getName() != null && !request.getName().equals(servicePackage.getName())) {
            if (packageRepository.findByName(request.getName()).isPresent()) {
                throw new ResourceConflictException("Gói dịch vụ có tên này đã tồn tại");
            }
            servicePackage.setName(request.getName());
        }
        if (request.getFeatures() != null) {
            servicePackage.setFeatures(request.getFeatures());
        }
        if (request.getPrice() != null) {
            servicePackage.setPrice(request.getPrice());
        }
        if (request.getDurationDays() != null) {
            servicePackage.setDurationDays(request.getDurationDays());
        }
        servicePackage = packageRepository.save(servicePackage);
        log.info("Subscription Service: Updated service package ID: {}", id);
        return mapToResponse(servicePackage);
    }
    @Transactional
    public void deletePackage(java.util.UUID id) {
        ServicePackage servicePackage = packageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PACKAGE_NOT_FOUND));
        // Soft delete bằng cách set isActive = false
        servicePackage.setActive(false);
        packageRepository.save(servicePackage);
        log.info("Subscription Service: Soft-deleted service package ID: {}", id);
    }
    public ServicePackageResponse mapToResponse(ServicePackage p) {
        return ServicePackageResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .features(p.getFeatures())
                .price(p.getPrice())
                .durationDays(p.getDurationDays())
                .build();
    }
}

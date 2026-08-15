package fu.stockspace.stockspace_be.common.service;

import fu.stockspace.stockspace_be.common.dto.CreateSystemPolicyRequest;
import fu.stockspace.stockspace_be.common.dto.SystemPolicyResponse;
import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.repository.SystemPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemPolicyService {

    private final SystemPolicyRepository systemPolicyRepository;

    @Transactional(readOnly = true)
    public SystemPolicyResponse getActivePolicy() {
        SystemPolicy policy = systemPolicyRepository.findFirstByIsActiveTrueAndIsDeletedFalseOrderByCreatedAtDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chính sách/cam kết ràng buộc hiệu lực nào trong hệ thống"));
        return mapToResponse(policy);
    }

    @Transactional
    public SystemPolicyResponse createPolicy(CreateSystemPolicyRequest request) {
        log.info("Admin creating new system policy version: {}", request.getVersion());


        boolean exists = systemPolicyRepository.findFirstByVersionAndIsDeletedFalse(request.getVersion()).isPresent();
        if (exists) {
            throw new ResourceConflictException("Phiên bản chính sách " + request.getVersion() + " đã tồn tại trong hệ thống");
        }


        List<SystemPolicy> activePolicies = systemPolicyRepository.findAllActivePolicies();
        for (SystemPolicy oldPolicy : activePolicies) {
            oldPolicy.setActive(false);
        }
        systemPolicyRepository.saveAll(activePolicies);

        SystemPolicy newPolicy = SystemPolicy.builder()
                .version(request.getVersion().trim())
                .content(request.getContent().trim())
                .isActive(true)
                .isDeleted(false)
                .build();

        newPolicy = systemPolicyRepository.save(newPolicy);
        log.info("New system policy version {} created and set as active", newPolicy.getVersion());
        return mapToResponse(newPolicy);
    }

    @Transactional(readOnly = true)
    public Page<SystemPolicyResponse> getAllPolicies(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return systemPolicyRepository.findAllPolicies(pageable)
                .map(this::mapToResponse);
    }

    private SystemPolicyResponse mapToResponse(SystemPolicy p) {
        return SystemPolicyResponse.builder()
                .id(p.getId())
                .version(p.getVersion())
                .content(p.getContent())
                .isActive(p.isActive())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}

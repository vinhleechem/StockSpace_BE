package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.warehouse.dto.CreateWarehouseTypeRequest;
import fu.stockspace.stockspace_be.warehouse.dto.PagedWarehouseTypeResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseTypeResponse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseType;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseTypeService {

    private final WarehouseTypeRepository warehouseTypeRepository;
    private final WarehouseRepository warehouseRepository;

    @Transactional
    public WarehouseTypeResponse createType(CreateWarehouseTypeRequest request) {
        log.info("Creating warehouse type: {}", request.getName());
        String nameTrimmed = request.getName().trim();
        if (warehouseTypeRepository.existsByName(nameTrimmed)) {
            throw new ResourceConflictException(ErrorCode.WAREHOUSE_TYPE_ALREADY_EXISTS);
        }

        WarehouseType warehouseType = WarehouseType.builder()
                .name(nameTrimmed)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        warehouseType = warehouseTypeRepository.save(warehouseType);
        return mapToResponse(warehouseType);
    }

    @Transactional
    public WarehouseTypeResponse updateType(java.util.UUID id, CreateWarehouseTypeRequest request) {
        log.info("Updating warehouse type ID: {}", id);
        WarehouseType warehouseType = warehouseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_TYPE_NOT_FOUND));

        String newNameTrimmed = request.getName().trim();
        if (!warehouseType.getName().equalsIgnoreCase(newNameTrimmed) && warehouseTypeRepository.existsByName(newNameTrimmed)) {
            throw new ResourceConflictException(ErrorCode.WAREHOUSE_TYPE_ALREADY_EXISTS);
        }

        warehouseType.setName(newNameTrimmed);
        warehouseType.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        warehouseType = warehouseTypeRepository.save(warehouseType);
        return mapToResponse(warehouseType);
    }

    @Transactional
    public void deleteType(java.util.UUID id) {
        log.info("Deleting warehouse type ID: {}", id);
        WarehouseType warehouseType = warehouseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_TYPE_NOT_FOUND));

        if (warehouseRepository.existsByTypeId(id)) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_TYPE_IN_USE);
        }

        warehouseType.setDeleted(true);
        warehouseTypeRepository.save(warehouseType);
        log.info("Deleted warehouse type ID: {}", id);
    }

    @Transactional(readOnly = true)
    public WarehouseTypeResponse getTypeById(java.util.UUID id) {
        WarehouseType warehouseType = warehouseTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_TYPE_NOT_FOUND));
        return mapToResponse(warehouseType);
    }

    @Transactional(readOnly = true)
    public List<WarehouseTypeResponse> getAllTypes() {
        return warehouseTypeRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PagedWarehouseTypeResponse getTypesPaged(String keyword, int page, int size, String sortBy, String sortDir) {
        Sort sort = "asc".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<WarehouseType> typePage;
        if (StringUtils.hasText(keyword)) {
            String formattedKeyword = "%" + keyword.trim().toLowerCase() + "%";
            typePage = warehouseTypeRepository.search(formattedKeyword, pageable);
        } else {
            typePage = warehouseTypeRepository.findAll(pageable);
        }

        List<WarehouseTypeResponse> content = typePage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PagedWarehouseTypeResponse.builder()
                .content(content)
                .page(typePage.getNumber())
                .size(typePage.getSize())
                .totalElements(typePage.getTotalElements())
                .totalPages(typePage.getTotalPages())
                .last(typePage.isLast())
                .build();
    }

    private WarehouseTypeResponse mapToResponse(WarehouseType wt) {
        return WarehouseTypeResponse.builder()
                .id(wt.getId())
                .name(wt.getName())
                .description(wt.getDescription())
                .build();
    }
}

package fu.stockspace.stockspace_be.contract.service;
import java.util.UUID;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.InternalServerException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.dto.*;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.dto.BulkLayoutSaveRequest;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;








@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {
    private static final long MIN_RENTAL_DURATION_DAYS = 7;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final RentalContractRepository contractRepository;
    private final WarehouseService warehouseService;
    private final UserRepository userRepository;
    private final WarehouseLayoutService warehouseLayoutService;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;
    private final WarehouseRentalAvailabilityService warehouseRentalAvailabilityService;






    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsTenant(UUID tenantId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByTenantId(tenantId, pageable)
                .map(contract -> mapToResponse(contract, tenantId));
    }



    @Transactional(readOnly = true)
    public Page<RentalContractResponse> getMyContractsAsOwner(UUID ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return contractRepository.findByOwnerId(ownerId, pageable)
                .map(contract -> mapToResponse(contract, ownerId));
    }



    @Transactional(readOnly = true)
    public RentalContractResponse getContractById(UUID contractId, UUID userId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID tenantId = contract.getTenant() != null ? contract.getTenant().getId() : null;
        UUID ownerId = contract.getOwner() != null ? contract.getOwner().getId() : null;
        if (!userId.equals(tenantId) && !userId.equals(ownerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return mapToResponse(contract, userId);
    }

    @Transactional(readOnly = true)
    public RentalContractResponse previewOwnerDraft(UUID ownerId, CreateRentalContractRequest request) {
        DraftTerms terms = resolveDraftTerms(ownerId, request);
        RentalContract draft = buildDraftContract(terms, request);
        RentalAreaAvailability availability = warehouseRentalAvailabilityService.calculate(
                terms.warehouse().getId(),
                null,
                terms.startDate(),
                terms.endDate(),
                terms.leasedAreaM2());
        return mapToResponse(draft, null, availability);
    }

    @Transactional
    public RentalContractResponse createOwnerDraft(UUID ownerId, CreateRentalContractRequest request) {
        DraftTerms terms = resolveDraftTerms(ownerId, request);
        WarehouseLayoutResponse layout = warehouseLayoutService.prepareTenantLayoutForDraft(
                terms.warehouse().getId(),
                terms.tenant().getId(),
                terms.leasedWidth(),
                terms.leasedLength(),
                terms.leasedHeight(),
                terms.pricingType() == RentalPricingType.FIXED_MONTHLY);

        RentalContract draft = buildDraftContract(terms, request);
        draft.setLayoutSnapshot(serializeLayoutSnapshot(layout));
        draft = contractRepository.save(draft);
        log.info("Owner {} created direct rental contract draft {} for tenant {} and warehouse {}",
                ownerId, draft.getId(), terms.tenant().getId(), terms.warehouse().getId());
        return mapToResponse(draft);
    }

    /**
     * Creates the owner-editable successor draft for an active direct contract.
     * All identity, scope and layout fields are copied from the locked source;
     * the owner can only provide the new end date and renewal-specific terms.
     */
    @Transactional
    public RentalContractResponse createRenewalDraft(UUID ownerId,
                                                     UUID sourceContractId,
                                                     CreateContractRenewalRequest request) {
        if (request == null || request.getEndDate() == null) {
            throw new BadRequestException("Renewal end date is required");
        }

        RentalContract source = contractRepository.findByIdForUpdate(sourceContractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        requireRenewalEligibility(ownerId, source);

        LocalDate renewalStartDate = source.getEndDate().plusDays(1);
        validateContractDates(renewalStartDate, request.getEndDate());

        Warehouse warehouse = source.getWarehouse();
        User tenant = source.getTenant();
        DraftTerms terms = resolveContractTerms(
                warehouse,
                tenant,
                renewalStartDate,
                request.getEndDate(),
                source.getLeasedWidth(),
                source.getLeasedLength(),
                source.getLeasedHeight(),
                request.getNegotiatedMonthlyRent());
        if (source.getLeasedAreaM2() == null
                || source.getLeasedAreaM2().compareTo(terms.leasedAreaM2()) != 0) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The source contract area is inconsistent with its dimensions");
        }

        WarehouseLayoutResponse layout = warehouseLayoutService
                .findActiveTenantLayoutForContract(warehouse.getId(), tenant.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND));
        warehouseLayoutService.validateContractLayout(
                layout,
                warehouse.getId(),
                tenant.getId(),
                terms.leasedWidth(),
                terms.leasedLength(),
                terms.leasedHeight());

        RentalContract renewal = RentalContract.builder()
                .owner(source.getOwner())
                .tenant(source.getTenant())
                .warehouse(source.getWarehouse())
                .renewedFromContract(source)
                .status(ContractStatus.DRAFT)
                .startDate(terms.startDate())
                .endDate(terms.endDate())
                .pricingType(terms.pricingType())
                .rentalPriceSnapshot(terms.rentalPriceSnapshot())
                .finalMonthlyRent(terms.finalMonthlyRent())
                .leasedWidth(terms.leasedWidth())
                .leasedLength(terms.leasedLength())
                .leasedHeight(terms.leasedHeight())
                .leasedAreaM2(terms.leasedAreaM2())
                .ownerNote(normalizeOptionalText(request.getOwnerNote()))
                .layoutSnapshot(serializeLayoutSnapshot(layout))
                .build();
        if (request.getPaperContractFiles() != null) {
            renewal.setPaperContractFiles(serializeJson(
                    request.getPaperContractFiles(), "Paper contract files must be valid JSON"));
        }

        try {
            renewal = contractRepository.save(renewal);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_RENEWAL_ALREADY_EXISTS,
                    "The source contract already has a renewal draft or successor");
        }
        log.info("Owner {} created renewal draft {} from source contract {}",
                ownerId, renewal.getId(), source.getId());
        return mapToResponse(renewal, ownerId);
    }

    /**
     * Updates only the mutable terms of a direct owner-created contract. The
     * owner, tenant and warehouse are deliberately taken from the persisted
     * contract and cannot be changed through this API.
     */
    @Transactional
    public RentalContractResponse updateOwnerDraft(UUID ownerId,
                                                   UUID contractId,
                                                   UpdateRentalContractRequest request) {
        RentalContract contract = findDirectContractForOwnerEdit(ownerId, contractId);
        if (contract.getStatus() != ContractStatus.DRAFT
                && contract.getStatus() != ContractStatus.CHANGES_REQUESTED) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Only DRAFT or CHANGES_REQUESTED contracts can be edited");
        }
        if (request == null) {
            throw new BadRequestException("Contract update request is required");
        }

        if (contract.getRenewedFromContract() != null) {
            return updateRenewalDraft(ownerId, contract, request);
        }

        Warehouse warehouse = contract.getWarehouse();
        User tenant = contract.getTenant();
        DraftTerms terms = resolveContractTerms(
                warehouse,
                tenant,
                request.getStartDate(),
                request.getEndDate(),
                request.getLeasedWidth(),
                request.getLeasedLength(),
                request.getLeasedHeight(),
                request.getNegotiatedMonthlyRent());

        boolean dimensionsChanged = !sameDimensions(
                contract.getLeasedWidth(), contract.getLeasedLength(), contract.getLeasedHeight(),
                terms.leasedWidth(), terms.leasedLength(), terms.leasedHeight());
        if (dimensionsChanged) {
            WarehouseLayoutResponse layout = warehouseLayoutService.prepareTenantLayoutForDraft(
                    warehouse.getId(),
                    tenant.getId(),
                    terms.leasedWidth(),
                    terms.leasedLength(),
                    terms.leasedHeight(),
                    terms.pricingType() == RentalPricingType.FIXED_MONTHLY);
            contract.setLayoutSnapshot(serializeLayoutSnapshot(layout));
        }

        applyDraftTerms(contract, terms, request.getOwnerNote());
        if (request.getPaperContractFiles() != null) {
            contract.setPaperContractFiles(serializeJson(
                    request.getPaperContractFiles(), "Paper contract files must be valid JSON"));
        }
        contract = contractRepository.save(contract);
        return mapToResponse(contract, ownerId);
    }

    private RentalContractResponse updateRenewalDraft(UUID ownerId,
                                                      RentalContract renewal,
                                                      UpdateRentalContractRequest request) {
        RentalContract source = renewal.getRenewedFromContract();
        requireRenewalMutationDeadline(renewal);
        if (!java.util.Objects.equals(renewal.getStartDate(), request.getStartDate())
                || !sameDimensions(
                renewal.getLeasedWidth(), renewal.getLeasedLength(), renewal.getLeasedHeight(),
                request.getLeasedWidth(), request.getLeasedLength(), request.getLeasedHeight())) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "Renewal start date and leased dimensions are inherited from the source contract");
        }

        Warehouse warehouse = renewal.getWarehouse();
        if (source == null || source.getPricingType() != renewal.getPricingType()
                || renewal.getPricingType() != currentPricingType(warehouse)) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_RENEWAL_PRICING_CHANGED);
        }
        DraftTerms terms = resolveContractTerms(
                warehouse,
                renewal.getTenant(),
                renewal.getStartDate(),
                request.getEndDate(),
                renewal.getLeasedWidth(),
                renewal.getLeasedLength(),
                renewal.getLeasedHeight(),
                request.getNegotiatedMonthlyRent());
        if (renewal.getLeasedAreaM2() == null
                || renewal.getLeasedAreaM2().compareTo(terms.leasedAreaM2()) != 0) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "Renewal leased area cannot be changed");
        }

        renewal.setEndDate(terms.endDate());
        renewal.setRentalPriceSnapshot(terms.rentalPriceSnapshot());
        renewal.setFinalMonthlyRent(terms.finalMonthlyRent());
        renewal.setOwnerNote(normalizeOptionalText(request.getOwnerNote()));
        if (request.getPaperContractFiles() != null) {
            renewal.setPaperContractFiles(serializeJson(
                    request.getPaperContractFiles(), "Paper contract files must be valid JSON"));
        }
        contractRepository.save(renewal);
        return mapToResponse(renewal, ownerId);
    }

    /**
     * Revalidates and submits a direct contract.
     * The warehouse row is locked before the overlap query so two concurrent
     * submissions for the same warehouse observe a serialized state.
     */
    @Transactional
    public RentalContractResponse submitOwnerContract(UUID ownerId, UUID contractId) {
        RentalContract contract = findDirectContractForOwnerEdit(ownerId, contractId);
        if (contract.getStatus() != ContractStatus.DRAFT
                && contract.getStatus() != ContractStatus.CHANGES_REQUESTED) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Only DRAFT or CHANGES_REQUESTED contracts can be submitted");
        }
        if (contract.getRenewedFromContract() != null) {
            return submitRenewalContract(ownerId, contract);
        }

        Warehouse lockedWarehouse = warehouseService.lockWarehouseForContractSubmit(
                contract.getWarehouse().getId());
        User tenant = contract.getTenant();
        RentalPricingType currentPricingType = lockedWarehouse.getRentalPricingType() != null
                ? lockedWarehouse.getRentalPricingType()
                : RentalPricingType.FIXED_MONTHLY;
        BigDecimal negotiatedMonthlyRent = currentPricingType == RentalPricingType.NEGOTIATED
                ? contract.getFinalMonthlyRent()
                : null;
        DraftTerms terms = resolveContractTerms(
                lockedWarehouse,
                tenant,
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getLeasedWidth(),
                contract.getLeasedLength(),
                contract.getLeasedHeight(),
                negotiatedMonthlyRent);

        if (contractRepository.existsDirectDateOverlapForSubmit(
                contract.getId(), tenant.getId(), lockedWarehouse.getId(),
                contract.getStartDate(), contract.getEndDate())) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_DATE_OVERLAP,
                    "The tenant already has an overlapping contract for this warehouse");
        }

        RentalAreaAvailability availability = warehouseRentalAvailabilityService.calculate(
                lockedWarehouse.getId(),
                contract.getId(),
                contract.getStartDate(),
                contract.getEndDate(),
                terms.leasedAreaM2());
        if (!availability.sufficient()) {
            throw new ResourceConflictException(
                    ErrorCode.WAREHOUSE_AREA_UNAVAILABLE,
                    "The requested leased area is not available for the selected dates");
        }

        requirePaperContractFiles(contract);
        Optional<WarehouseLayoutResponse> currentLayout = warehouseLayoutService
                .findActiveTenantLayoutForContract(lockedWarehouse.getId(), tenant.getId());
        WarehouseLayoutResponse layout;
        if (currentLayout.isPresent()) {
            layout = currentLayout.get();
        } else {
            if (!hasLayoutSnapshot(contract)) {
                throw new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND);
            }
            layout = readLayoutSnapshot(contract.getLayoutSnapshot());
        }
        warehouseLayoutService.validateContractLayout(
                layout,
                lockedWarehouse.getId(),
                tenant.getId(),
                terms.leasedWidth(),
                terms.leasedLength(),
                terms.leasedHeight());

        applyDraftTerms(contract, terms, contract.getOwnerNote());
        contract.setLayoutSnapshot(serializeLayoutSnapshot(layout));
        if (contract.getChangeRequestReason() != null && !contract.getChangeRequestReason().isBlank()) {
            log.info("Owner {} resubmitting contract {} after tenant change request: {}",
                    ownerId, contract.getId(), contract.getChangeRequestReason());
        }
        contract.setChangeRequestReason(null);
        contract.setRejectionReason(null);
        contract.setSubmittedAt(LocalDateTime.now());
        contract.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);
        contract = contractRepository.save(contract);

        notifyTenantOfSubmission(contract, tenant, lockedWarehouse);
        return mapToResponse(contract, ownerId, availability);
    }

    /**
     * Revalidates and submits a renewal successor. Lock order is deliberately
     * warehouse, source contract, successor so concurrent renewal operations
     * serialize around the same physical warehouse and source lifecycle.
     */
    private RentalContractResponse submitRenewalContract(UUID ownerId,
                                                          RentalContract candidate) {
        UUID sourceContractId = candidate.getRenewedFromContract().getId();
        Warehouse lockedWarehouse = warehouseService.lockWarehouseForContractSubmit(
                candidate.getWarehouse().getId());
        RentalContract source = contractRepository.findByIdForUpdate(sourceContractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        RentalContract renewal = contractRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        renewal.setRenewedFromContract(source);

        if (renewal.getOwner() == null || !ownerId.equals(renewal.getOwner().getId())
                || renewal.getTenant() == null || renewal.getWarehouse() == null
                || !lockedWarehouse.getId().equals(renewal.getWarehouse().getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (renewal.getStatus() != ContractStatus.DRAFT
                && renewal.getStatus() != ContractStatus.CHANGES_REQUESTED) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Only DRAFT or CHANGES_REQUESTED renewal contracts can be submitted");
        }
        requireRenewalMutationDeadline(renewal);
        if (source.getRenewedFromContract() != null
                || source.getStatus() != ContractStatus.ACTIVE
                || !source.isActive() || source.isDeleted()
                || source.getEndDate() == null
                || !source.getEndDate().plusDays(1).equals(renewal.getStartDate())) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The renewal successor no longer matches the source contract lifecycle");
        }
        if (renewal.getPricingType() != source.getPricingType()
                || renewal.getPricingType() != currentPricingType(lockedWarehouse)) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_RENEWAL_PRICING_CHANGED);
        }
        if (contractRepository.findBlockingRenewalsBySourceId(source.getId()).stream()
                .anyMatch(other -> !candidate.getId().equals(other.getId()))) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_RENEWAL_ALREADY_EXISTS);
        }

        WarehouseLayoutResponse defaultLayout = warehouseLayoutService
                .getDefaultLayoutForContract(lockedWarehouse.getId());
        RentalAreaAllocationPolicy.LeasedDimensions dimensions;
        try {
            dimensions = RentalAreaAllocationPolicy.validatePreservedDimensions(
                    source.getPricingType(),
                    source.getLeasedWidth(),
                    source.getLeasedLength(),
                    source.getLeasedHeight(),
                    defaultLayout);
        } catch (BadRequestException exception) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The source contract dimensions are no longer valid for the warehouse");
        }
        if (source.getLeasedAreaM2() == null
                || source.getLeasedAreaM2().compareTo(dimensions.areaM2()) != 0
                || renewal.getLeasedAreaM2() == null
                || renewal.getLeasedAreaM2().compareTo(source.getLeasedAreaM2()) != 0
                || !sameDimensions(
                renewal.getLeasedWidth(), renewal.getLeasedLength(), renewal.getLeasedHeight(),
                source.getLeasedWidth(), source.getLeasedLength(), source.getLeasedHeight())) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "Renewal scope must remain identical to the source contract");
        }

        DraftTerms terms = resolveContractTerms(
                lockedWarehouse,
                renewal.getTenant(),
                renewal.getStartDate(),
                renewal.getEndDate(),
                source.getLeasedWidth(),
                source.getLeasedLength(),
                source.getLeasedHeight(),
                renewal.getPricingType() == RentalPricingType.NEGOTIATED
                        ? renewal.getFinalMonthlyRent() : null);
        if (contractRepository.existsDirectDateOverlapForSubmit(
                renewal.getId(), renewal.getTenant().getId(), lockedWarehouse.getId(),
                renewal.getStartDate(), renewal.getEndDate())) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_DATE_OVERLAP,
                    "The tenant already has an overlapping contract for this warehouse");
        }
        RentalAreaAvailability availability = warehouseRentalAvailabilityService.calculate(
                lockedWarehouse.getId(),
                renewal.getId(),
                renewal.getStartDate(),
                renewal.getEndDate(),
                terms.leasedAreaM2());
        if (!availability.sufficient()) {
            throw new ResourceConflictException(
                    ErrorCode.WAREHOUSE_AREA_UNAVAILABLE,
                    "The requested leased area is not available for the selected dates");
        }

        requirePaperContractFiles(renewal);
        WarehouseLayoutResponse layout = warehouseLayoutService
                .findActiveTenantLayoutForContract(lockedWarehouse.getId(), renewal.getTenant().getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND));
        warehouseLayoutService.validateContractLayout(
                layout,
                lockedWarehouse.getId(),
                renewal.getTenant().getId(),
                terms.leasedWidth(),
                terms.leasedLength(),
                terms.leasedHeight());

        applyDraftTerms(renewal, terms, renewal.getOwnerNote());
        renewal.setLayoutSnapshot(serializeLayoutSnapshot(layout));
        renewal.setChangeRequestReason(null);
        renewal.setRejectionReason(null);
        renewal.setSubmittedAt(LocalDateTime.now());
        renewal.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);
        renewal = contractRepository.save(renewal);

        notifyTenantOfSubmission(renewal, renewal.getTenant(), lockedWarehouse);
        return mapToResponse(renewal, ownerId, availability);
    }

    private RentalContract findDirectContractForOwnerEdit(UUID ownerId, UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.isActive() || contract.isDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        if (contract.getOwner() == null || contract.getTenant() == null || contract.getWarehouse() == null) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "This operation requires a direct rental contract");
        }
        if (!ownerId.equals(contract.getOwner().getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return contract;
    }

    private void applyDraftTerms(RentalContract contract, DraftTerms terms, String ownerNote) {
        contract.setStartDate(terms.startDate());
        contract.setEndDate(terms.endDate());
        contract.setPricingType(terms.pricingType());
        contract.setRentalPriceSnapshot(terms.rentalPriceSnapshot());
        contract.setFinalMonthlyRent(terms.finalMonthlyRent());
        contract.setLeasedWidth(terms.leasedWidth());
        contract.setLeasedLength(terms.leasedLength());
        contract.setLeasedHeight(terms.leasedHeight());
        contract.setLeasedAreaM2(terms.leasedAreaM2());
        contract.setOwnerNote(normalizeOptionalText(ownerNote));
    }

    private boolean sameDimensions(BigDecimal firstWidth,
                                   BigDecimal firstLength,
                                   BigDecimal firstHeight,
                                   BigDecimal secondWidth,
                                   BigDecimal secondLength,
                                   BigDecimal secondHeight) {
        return firstWidth != null && firstLength != null && firstHeight != null
                && secondWidth != null && secondLength != null && secondHeight != null
                && firstWidth.compareTo(secondWidth) == 0
                && firstLength.compareTo(secondLength) == 0
                && firstHeight.compareTo(secondHeight) == 0;
    }

    private void requirePaperContractFiles(RentalContract contract) {
        String files = contract.getPaperContractFiles();
        if (files == null || files.isBlank()) {
            throw new BadRequestException(ErrorCode.PAPER_CONTRACT_REQUIRED);
        }
        try {
            JsonNode node = objectMapper.readTree(files);
            boolean hasInvalidFile = false;
            if (node.isArray()) {
                for (JsonNode file : node) {
                    if (!file.isTextual() || file.asText().isBlank()) {
                        hasInvalidFile = true;
                        break;
                    }
                }
            }
            if (!node.isArray() || node.isEmpty() || hasInvalidFile) {
                throw new BadRequestException(ErrorCode.PAPER_CONTRACT_REQUIRED);
            }
        } catch (JsonProcessingException e) {
            throw new BadRequestException(ErrorCode.PAPER_CONTRACT_REQUIRED);
        }
    }

    private void notifyTenantOfSubmission(RentalContract contract, User tenant, Warehouse warehouse) {
        try {
            notificationService.push(
                    tenant.getId(),
                    "Rental contract requires confirmation",
                    "The owner submitted a rental contract for warehouse " + warehouse.getName() + ".",
                    "CONTRACT_SUBMITTED");
        } catch (Exception e) {
            log.warn("Failed to push direct contract notification for {}: {}",
                    contract.getId(), e.getMessage());
        }
    }

    /**
     * Confirms a direct contract submitted by its owner. This lifecycle is
     * deliberately independent from platform wallet flows: tenant confirmation
     * only activates the contract and does not
     * change the warehouse listing status.
     */
    @Transactional
    public RentalContractResponse confirmDirectContract(UUID tenantId, UUID contractId) {
        RentalContract contract = findDirectContractForTenantReview(tenantId, contractId);
        requirePendingTenantConfirmation(contract);

        Warehouse lockedWarehouse = warehouseService.lockWarehouseForContractSubmit(
                contract.getWarehouse().getId());
        validateContractDates(contract.getStartDate(), contract.getEndDate());
        if (contractRepository.existsDirectDateOverlapForSubmit(
                contract.getId(), tenantId, lockedWarehouse.getId(),
                contract.getStartDate(), contract.getEndDate())) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_DATE_OVERLAP,
                    "The tenant already has an overlapping contract for this warehouse");
        }

        RentalAreaAvailability availability = warehouseRentalAvailabilityService.calculate(
                lockedWarehouse.getId(),
                contract.getId(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getLeasedAreaM2());
        if (!availability.sufficient()) {
            throw new ResourceConflictException(
                    ErrorCode.WAREHOUSE_AREA_UNAVAILABLE,
                    "The requested leased area is not available for the selected dates");
        }

        contract.setConfirmedAt(LocalDateTime.now());
        contract.setStatus(ContractStatus.ACTIVE);
        contract = contractRepository.save(contract);

        notifyOwnerOfTenantDecision(
                contract,
                "Rental contract confirmed",
                "The tenant confirmed the rental contract for warehouse "
                        + lockedWarehouse.getName() + ".",
                "CONTRACT_CONFIRMED");
        return mapToResponse(contract, tenantId, availability);
    }

    /**
     * Moves a submitted direct contract back to the owner for correction.
     * Tenant review does not mutate the submitted terms or layout; the owner
     * edit/resubmit flow owns those mutations.
     */
    @Transactional
    public RentalContractResponse requestDirectContractChanges(
            UUID tenantId,
            UUID contractId,
            TenantContractDecisionRequest request) {
        RentalContract contract = findDirectContractForTenantReview(tenantId, contractId);
        requirePendingTenantConfirmation(contract);
        String reason = normalizeRequiredDecisionReason(request);

        contract.setChangeRequestReason(reason);
        contract.setRejectionReason(null);
        contract.setConfirmedAt(null);
        contract.setStatus(ContractStatus.CHANGES_REQUESTED);
        contract = contractRepository.save(contract);

        notifyOwnerOfTenantDecision(
                contract,
                "Rental contract changes requested",
                "The tenant requested changes to the rental contract for warehouse "
                        + contract.getWarehouse().getName() + ". Reason: " + reason,
                "CONTRACT_CHANGES_REQUESTED");
        return mapToResponse(contract, tenantId);
    }

    /**
     * Rejects a submitted direct contract while preserving it as read-only
     * history. The tenant layout proposal is archived only when the tenant
     * has no other active contract using the same warehouse.
     */
    @Transactional
    public RentalContractResponse rejectDirectContract(
            UUID tenantId,
            UUID contractId,
            TenantContractDecisionRequest request) {
        RentalContract contract = findDirectContractForTenantReview(tenantId, contractId);
        requirePendingTenantConfirmation(contract);
        String reason = normalizeRequiredDecisionReason(request);

        contract.setRejectionReason(reason);
        contract.setChangeRequestReason(null);
        contract.setConfirmedAt(null);
        contract.setStatus(ContractStatus.REJECTED);
        contract = contractRepository.save(contract);

        if (!contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(
                tenantId, contract.getWarehouse().getId())) {
            warehouseLayoutService.archiveTenantLayout(
                    contract.getWarehouse().getId(), tenantId);
        }

        notifyOwnerOfTenantDecision(
                contract,
                "Rental contract rejected",
                "The tenant rejected the rental contract for warehouse "
                        + contract.getWarehouse().getName() + ". Reason: " + reason,
                "CONTRACT_REJECTED");
        return mapToResponse(contract, tenantId);
    }

    private RentalContract findDirectContractForTenantReview(UUID tenantId, UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.isActive() || contract.isDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        if (contract.getOwner() == null || contract.getTenant() == null
                || contract.getWarehouse() == null) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "This operation requires a direct rental contract");
        }
        if (!tenantId.equals(contract.getTenant().getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return contract;
    }

    private void requirePendingTenantConfirmation(RentalContract contract) {
        if (contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Contract must be in PENDING_TENANT_CONFIRM status for this action");
        }
    }

    private String normalizeRequiredDecisionReason(TenantContractDecisionRequest request) {
        if (request == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BadRequestException("Reason is required");
        }
        String reason = request.getReason().trim();
        if (reason.length() > 2000) {
            throw new BadRequestException("Reason must not exceed 2000 characters");
        }
        return reason;
    }

    private void notifyOwnerOfTenantDecision(RentalContract contract,
                                             String title,
                                             String message,
                                             String type) {
        try {
            notificationService.push(
                    contract.getOwner().getId(), title, message, type);
        } catch (Exception e) {
            log.warn("Failed to push direct contract decision notification for {}: {}",
                    contract.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public WarehouseLayoutResponse getOwnerContractLayout(UUID ownerId, UUID contractId) {
        RentalContract contract = findContractForLayout(contractId);
        requireContractOwner(contract, ownerId);
        return getCurrentOrSnapshotLayout(contract);
    }

    @Transactional
    public WarehouseLayoutResponse updateOwnerContractLayout(UUID ownerId,
                                                             UUID contractId,
                                                             BulkLayoutSaveRequest request) {
        RentalContract contract = findContractForLayout(contractId);
        requireContractOwner(contract, ownerId);
        if (contract.getStatus() != ContractStatus.DRAFT
                && contract.getStatus() != ContractStatus.CHANGES_REQUESTED) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Contract layout can only be edited in DRAFT or CHANGES_REQUESTED");
        }
        if (contract.getRenewedFromContract() != null) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Renewal contract layout is inherited from the active tenant layout");
        }
        if (contract.getPricingType() == RentalPricingType.FIXED_MONTHLY) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "FIXED_MONTHLY contract layout is derived from the warehouse default layout");
        }
        validateContractLayoutDimensions(contract, request);

        Warehouse warehouse = contract.getWarehouse();
        User tenant = contract.getTenant();
        if (warehouse == null || tenant == null) {
            throw new BadRequestException("Contract relations are incomplete");
        }

        WarehouseLayoutResponse savedLayout = warehouseLayoutService.saveContractLayout(
                warehouse.getId(), tenant.getId(), request);
        contract.setLayoutSnapshot(serializeLayoutSnapshot(savedLayout));
        contractRepository.save(contract);
        return savedLayout;
    }

    @Transactional(readOnly = true)
    public WarehouseLayoutResponse getTenantContractLayout(UUID tenantId, UUID contractId) {
        RentalContract contract = findContractForLayout(contractId);
        User tenant = contract.getTenant();
        if (tenant == null || !tenantId.equals(tenant.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (!isTenantLayoutReadable(contract.getStatus())) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Contract layout is not available in the current contract state");
        }
        return getCurrentOrSnapshotLayout(contract);
    }

    private RentalContract findContractForLayout(UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.isActive() || contract.isDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        return contract;
    }

    private void requireContractOwner(RentalContract contract, UUID ownerId) {
        User owner = contract.getOwner();
        if (owner == null || !ownerId.equals(owner.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private WarehouseLayoutResponse getCurrentOrSnapshotLayout(RentalContract contract) {
        Warehouse warehouse = contract.getWarehouse();
        User tenant = contract.getTenant();
        if (warehouse == null || tenant == null) {
            throw new BadRequestException("Contract relations are incomplete");
        }

        boolean preferSnapshot = contract.getStatus() == ContractStatus.REJECTED
                || contract.getStatus() == ContractStatus.EXPIRED
                || (contract.getStatus() != ContractStatus.ACTIVE
                && contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(
                tenant.getId(), warehouse.getId()));
        if (preferSnapshot && hasLayoutSnapshot(contract)) {
            return readLayoutSnapshot(contract.getLayoutSnapshot());
        }

        Optional<WarehouseLayoutResponse> currentLayout = warehouseLayoutService
                .findActiveTenantLayoutForContract(warehouse.getId(), tenant.getId());
        if (currentLayout.isPresent()) {
            return currentLayout.get();
        }
        if (!hasLayoutSnapshot(contract)) {
            throw new ResourceNotFoundException(ErrorCode.LAYOUT_NOT_FOUND);
        }
        return readLayoutSnapshot(contract.getLayoutSnapshot());
    }

    private boolean hasLayoutSnapshot(RentalContract contract) {
        return contract.getLayoutSnapshot() != null && !contract.getLayoutSnapshot().isBlank();
    }

    private WarehouseLayoutResponse readLayoutSnapshot(String snapshot) {
        try {
            WarehouseLayoutResponse layout = objectMapper.readValue(snapshot, WarehouseLayoutResponse.class);
            if (layout.getRacks() != null) {
                layout.getRacks().forEach(rack -> {
                    if (rack.getShelfCount() == null) {
                        int shelfCount = rack.getBins() == null
                                ? 1
                                : rack.getBins().stream()
                                .mapToInt(bin -> bin.getShelfLevel() == null ? 1 : bin.getShelfLevel())
                                .max()
                                .orElse(1);
                        rack.setShelfCount(Math.max(1, shelfCount));
                    }
                    if (rack.getMaxBinCount() == null) {
                        rack.setMaxBinCount(Math.max(1,
                                rack.getBins() == null ? 0 : rack.getBins().size()));
                    }
                });
            }
            return layout;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Contract layout snapshot is invalid");
        }
    }

    private void validateContractLayoutDimensions(RentalContract contract,
                                                   BulkLayoutSaveRequest request) {
        if (request == null || request.getWidth() == null || request.getLength() == null
                || request.getHeight() == null) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "Contract layout dimensions are required");
        }
        if (contract.getLeasedWidth() == null || contract.getLeasedLength() == null
                || contract.getLeasedHeight() == null
                || request.getWidth().compareTo(contract.getLeasedWidth()) != 0
                || request.getLength().compareTo(contract.getLeasedLength()) != 0
                || request.getHeight().compareTo(contract.getLeasedHeight()) != 0) {
            throw new BadRequestException(ErrorCode.INVALID_LEASE_DIMENSIONS,
                    "Contract layout dimensions cannot be changed");
        }
    }

    private boolean isTenantLayoutReadable(ContractStatus status) {
        return status == ContractStatus.PENDING_TENANT_CONFIRM
                || status == ContractStatus.CHANGES_REQUESTED
                || status == ContractStatus.ACTIVE
                || status == ContractStatus.REJECTED
                || status == ContractStatus.EXPIRED;
    }

    private String serializeLayoutSnapshot(WarehouseLayoutResponse layout) {
        return serializeJson(
                warehouseLayoutService.stabilizeLayoutSnapshot(layout),
                "Layout snapshot must be valid JSON");
    }

    @Transactional
    public void deleteOwnerDraft(UUID ownerId, UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS,
                    "Only DRAFT contracts can be deleted");
        }
        User owner = contract.getOwner();
        if (owner == null || !ownerId.equals(owner.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        if (contract.getRenewedFromContract() != null) {
            requireRenewalMutationDeadline(contract);
            contract.setActive(false);
            contract.setDeleted(true);
            contractRepository.save(contract);
            return;
        }

        contract.setActive(false);
        contract.setDeleted(true);
        contractRepository.save(contract);

        Warehouse warehouse = contract.getWarehouse();
        User tenant = contract.getTenant();
        if (warehouse != null && tenant != null
                && !contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(
                tenant.getId(), warehouse.getId())) {
            warehouseLayoutService.archiveTenantLayout(warehouse.getId(), tenant.getId());
        }
    }

    private DraftTerms resolveDraftTerms(UUID ownerId, CreateRentalContractRequest request) {
        if (request == null) {
            throw new BadRequestException("Contract request is required");
        }
        if (request.getWarehouseId() == null) {
            throw new BadRequestException("Warehouse is required");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("Start date and end date are required");
        }
        validateContractDates(request.getStartDate(), request.getEndDate());

        Warehouse warehouse = warehouseService.getOwnedWarehouseForContract(ownerId, request.getWarehouseId());
        String tenantEmail = request.getTenantEmail().trim();
        User tenant = userRepository.findActiveByEmailAndRole(
                        tenantEmail, RoleType.ROLE_TENANT.name())
                .orElseGet(() -> resolveTenantLookupFailure(tenantEmail));

        return resolveContractTerms(
                warehouse,
                tenant,
                request.getStartDate(),
                request.getEndDate(),
                request.getLeasedWidth(),
                request.getLeasedLength(),
                request.getLeasedHeight(),
                request.getNegotiatedMonthlyRent());
    }

    private DraftTerms resolveContractTerms(Warehouse warehouse,
                                            User tenant,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            BigDecimal leasedWidth,
                                            BigDecimal leasedLength,
                                            BigDecimal leasedHeight,
                                            BigDecimal negotiatedMonthlyRent) {
        if (warehouse == null || tenant == null) {
            throw new BadRequestException("Contract relations are incomplete");
        }
        validateContractDates(startDate, endDate);
        if (!tenant.isActive() || tenant.isDeleted()) {
            throw new BadRequestException(ErrorCode.INVALID_ROLE,
                    "Tenant account must be active");
        }
        if (!warehouse.isActive() || warehouse.isDeleted()
                || warehouse.getStatus() == WarehouseStatus.INACTIVE) {
            throw new BadRequestException("Warehouse must be active");
        }

        RentalPricingType pricingType = currentPricingType(warehouse);
        WarehouseLayoutResponse defaultLayout = warehouseLayoutService
                .getDefaultLayoutForContract(warehouse.getId());
        RentalAreaAllocationPolicy.LeasedDimensions dimensions =
                RentalAreaAllocationPolicy.resolveDimensions(
                        pricingType,
                        leasedWidth,
                        leasedLength,
                        leasedHeight,
                        defaultLayout);
        BigDecimal rentalPrice = warehouse.getRentalPrice();
        BigDecimal area = dimensions.areaM2();
        BigDecimal finalMonthlyRent;
        BigDecimal rentalPriceSnapshot;

        if (pricingType == RentalPricingType.NEGOTIATED) {
            if (negotiatedMonthlyRent == null || negotiatedMonthlyRent.signum() <= 0) {
                throw new BadRequestException(
                        "Negotiated monthly rent is required and must be greater than 0");
            }
            rentalPriceSnapshot = null;
            finalMonthlyRent = negotiatedMonthlyRent;
        } else {
            if (negotiatedMonthlyRent != null) {
                throw new BadRequestException(
                        "Negotiated monthly rent is allowed only for NEGOTIATED pricing");
            }
            if (rentalPrice == null || rentalPrice.signum() <= 0) {
                throw new BadRequestException("Warehouse rental price must be greater than 0");
            }
            rentalPriceSnapshot = rentalPrice;
            finalMonthlyRent = pricingType == RentalPricingType.FIXED_MONTHLY
                    ? rentalPrice
                    : rentalPrice.multiply(area);
        }

        return new DraftTerms(
                tenant,
                warehouse,
                pricingType,
                rentalPriceSnapshot,
                finalMonthlyRent,
                startDate,
                endDate,
                dimensions.width(),
                dimensions.length(),
                dimensions.height(),
                area);
    }

    private void validateContractDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException("Start date and end date are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("Start date must not be after end date");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) < MIN_RENTAL_DURATION_DAYS) {
            throw new BadRequestException("Rental duration must be at least 7 days");
        }
        if (endDate.isBefore(LocalDate.now())) {
            throw new BadRequestException("Contract end date must not be in the past");
        }
    }

    private RentalPricingType currentPricingType(Warehouse warehouse) {
        return warehouse.getRentalPricingType() != null
                ? warehouse.getRentalPricingType()
                : RentalPricingType.FIXED_MONTHLY;
    }


    private User resolveTenantLookupFailure(String tenantEmail) {
        User user = userRepository.findByEmailIgnoreCase(tenantEmail)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.TENANT_NOT_FOUND,
                        "Active tenant account was not found for the supplied email"));
        boolean hasTenantRole = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> RoleType.ROLE_TENANT.name().equals(role.getName()));
        if (!hasTenantRole) {
            throw new BadRequestException(ErrorCode.INVALID_ROLE,
                    "The supplied account does not have the TENANT role");
        }
        throw new ResourceNotFoundException(
                ErrorCode.TENANT_NOT_FOUND,
                "Active tenant account was not found for the supplied email");
    }

    private RentalContract buildDraftContract(DraftTerms terms, CreateRentalContractRequest request) {
        RentalContract draft = RentalContract.builder()
                .owner(terms.warehouse().getOwner())
                .tenant(terms.tenant())
                .warehouse(terms.warehouse())
                .status(ContractStatus.DRAFT)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .pricingType(terms.pricingType())
                .rentalPriceSnapshot(terms.rentalPriceSnapshot())
                .finalMonthlyRent(terms.finalMonthlyRent())
                .leasedWidth(terms.leasedWidth())
                .leasedLength(terms.leasedLength())
                .leasedHeight(terms.leasedHeight())
                .leasedAreaM2(terms.leasedAreaM2())
                .ownerNote(normalizeOptionalText(request.getOwnerNote()))
                .build();
        if (request.getPaperContractFiles() != null) {
            draft.setPaperContractFiles(serializeJson(
                    request.getPaperContractFiles(), "Paper contract files must be valid JSON"));
        }
        return draft;
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String serializeJson(Object value, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BadRequestException(errorMessage);
        }
    }

    private record DraftTerms(
            User tenant,
            Warehouse warehouse,
            RentalPricingType pricingType,
            BigDecimal rentalPriceSnapshot,
            BigDecimal finalMonthlyRent,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal leasedWidth,
            BigDecimal leasedLength,
            BigDecimal leasedHeight,
            BigDecimal leasedAreaM2) {
    }










    public RentalContractResponse mapToResponse(RentalContract c) {
        return mapToResponse(c, null, null);
    }

    public RentalContractResponse mapToResponse(RentalContract c, UUID viewerId) {
        return mapToResponse(c, viewerId, null);
    }

    private RentalContractResponse mapToResponse(RentalContract c,
                                                  UUID viewerId,
                                                  RentalAreaAvailability availability) {
        var tenant = c.getTenant();
        var warehouse = c.getWarehouse();
        var owner = c.getOwner();
        if (owner == null && warehouse != null) {
            owner = warehouse.getOwner();
        }
        RentalContract sourceContract = c.getRenewedFromContract();
        List<RentalContract> blockingRenewals = sourceContract == null && c.getId() != null
                ? contractRepository.findBlockingRenewalsBySourceId(c.getId())
                : List.of();
        List<String> paperContractFiles = deserializePaperContractFiles(c.getPaperContractFiles());
        ActionFlags actionFlags = calculateActionFlags(c, viewerId, tenant, owner);
        return RentalContractResponse.builder()
                .id(c.getId())
                .status(c.getStatus().name())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .paperContractFiles(paperContractFiles)
                .tenantId(tenant != null ? tenant.getId() : null)
                .tenantName(tenant != null ? tenant.getFullName() : null)
                .tenantEmail(tenant != null ? tenant.getEmail() : null)
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .warehouseAddress(warehouse != null ? warehouse.getAddress() : null)
                .ownerId(owner != null ? owner.getId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .canEdit(actionFlags.canEdit())
                .canDelete(actionFlags.canDelete())
                .canSubmit(actionFlags.canSubmit())
                .canConfirm(actionFlags.canConfirm())
                .canRequestChanges(actionFlags.canRequestChanges())
                .canReject(actionFlags.canReject())
                .canViewLayout(actionFlags.canViewLayout())
                .canManageWms(actionFlags.canManageWms())
                .layoutSetupRequired(actionFlags.layoutSetupRequired())
                .canEditContractLayout(actionFlags.canEditContractLayout())
                .pricingType(c.getPricingType())
                .rentalPriceSnapshot(c.getRentalPriceSnapshot())
                .finalMonthlyRent(c.getFinalMonthlyRent())
                .leasedWidth(c.getLeasedWidth())
                .leasedLength(c.getLeasedLength())
                .leasedHeight(c.getLeasedHeight())
                .leasedAreaM2(c.getLeasedAreaM2())
                .warehouseTotalAreaM2(availability != null ? availability.totalAreaM2() : null)
                .warehouseReservedAreaM2(availability != null ? availability.reservedAreaM2() : null)
                .warehouseAvailableAreaM2(availability != null ? availability.availableAreaM2() : null)
                .areaAvailabilitySufficient(availability != null ? availability.sufficient() : null)
                .ownerNote(c.getOwnerNote())
                .layoutSnapshot(c.getLayoutSnapshot())
                .changeRequestReason(c.getChangeRequestReason())
                .rejectionReason(c.getRejectionReason())
                .renewedFromContractId(sourceContract != null ? sourceContract.getId() : null)
                .renewalContractId(blockingRenewals.isEmpty() ? null : blockingRenewals.get(0).getId())
                .canCreateRenewal(actionFlags.canCreateRenewal())
                .confirmedAt(c.getConfirmedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .submittedAt(c.getSubmittedAt())
                .build();
    }

    private List<String> deserializePaperContractFiles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            throw new InternalServerException(ErrorCode.SYSTEM_ERROR,
                    "Stored paper contract files are invalid JSON");
        }
    }

    private ActionFlags calculateActionFlags(RentalContract contract,
                                             UUID viewerId,
                                             User tenant,
                                             User owner) {
        if (viewerId == null) {
            return ActionFlags.NONE;
        }
        boolean ownerViewer = owner != null && viewerId.equals(owner.getId());
        boolean tenantViewer = tenant != null && viewerId.equals(tenant.getId());
        ContractStatus status = contract.getStatus();
        boolean mutableStatus = status == ContractStatus.DRAFT
                || status == ContractStatus.CHANGES_REQUESTED;
        boolean ownerCanEdit = ownerViewer && mutableStatus;
        boolean canEditContractLayout = ownerCanEdit
                && contract.getPricingType() != RentalPricingType.FIXED_MONTHLY;
        boolean layoutSetupRequired = canEditContractLayout;
        boolean tenantCanReview = tenantViewer && status == ContractStatus.PENDING_TENANT_CONFIRM;
        boolean tenantCanViewLayout = tenantViewer && isTenantLayoutReadable(status);
        boolean canManageWms = tenantViewer
                && status == ContractStatus.ACTIVE
                && subscriptionService != null
                && subscriptionService.hasActiveSubscription(tenant.getId());
        boolean canCreateRenewal = ownerViewer && isRenewalEligible(contract, owner);
        return new ActionFlags(
                ownerCanEdit,
                ownerViewer && status == ContractStatus.DRAFT,
                ownerCanEdit,
                tenantCanReview,
                tenantCanReview,
                tenantCanReview,
                (ownerViewer && contract.isActive() && !contract.isDeleted()) || tenantCanViewLayout,
                canManageWms,
                layoutSetupRequired,
                canEditContractLayout,
                canCreateRenewal);
    }

    private void requireRenewalEligibility(UUID ownerId, RentalContract sourceContract) {
        if (sourceContract.getOwner() == null || sourceContract.getTenant() == null
                || sourceContract.getWarehouse() == null) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "Only a complete direct rental contract can be renewed");
        }
        if (!ownerId.equals(sourceContract.getOwner().getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (sourceContract.getRenewedFromContract() != null
                || sourceContract.getStatus() != ContractStatus.ACTIVE
                || !sourceContract.isActive()
                || sourceContract.isDeleted()) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "Only an active source contract without an existing renewal can be renewed");
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (sourceContract.getStartDate() == null || sourceContract.getEndDate() == null
                || today.isBefore(sourceContract.getStartDate())) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The source contract is not currently active");
        }
        if (today.isAfter(sourceContract.getEndDate())) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_DEADLINE_PASSED,
                    "The renewal deadline has passed");
        }

        Warehouse warehouse = sourceContract.getWarehouse();
        User tenant = sourceContract.getTenant();
        if (!warehouse.isActive() || warehouse.isDeleted()
                || !tenant.isActive() || tenant.isDeleted()) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The owner, tenant and warehouse must remain active");
        }
        if (!contractRepository.findBlockingRenewalsBySourceId(sourceContract.getId()).isEmpty()) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_RENEWAL_ALREADY_EXISTS);
        }

        WarehouseLayoutResponse defaultLayout = warehouseLayoutService
                .getDefaultLayoutForContract(warehouse.getId());
        RentalAreaAllocationPolicy.LeasedDimensions preservedDimensions;
        try {
            preservedDimensions = RentalAreaAllocationPolicy.validatePreservedDimensions(
                    sourceContract.getPricingType(),
                    sourceContract.getLeasedWidth(),
                    sourceContract.getLeasedLength(),
                    sourceContract.getLeasedHeight(),
                    defaultLayout);
        } catch (BadRequestException exception) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The source contract dimensions are no longer valid for the warehouse");
        }
        if (sourceContract.getLeasedAreaM2() == null
                || sourceContract.getLeasedAreaM2().compareTo(preservedDimensions.areaM2()) != 0) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The source contract area is inconsistent with its dimensions");
        }

        if (sourceContract.getPricingType() != currentPricingType(warehouse)) {
            throw new ResourceConflictException(ErrorCode.CONTRACT_RENEWAL_PRICING_CHANGED);
        }
        if (warehouseLayoutService.findActiveTenantLayoutForContract(
                warehouse.getId(), tenant.getId()).isEmpty()) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The active tenant layout is required before renewal");
        }
    }

    private void requireRenewalMutationDeadline(RentalContract renewal) {
        RentalContract source = renewal.getRenewedFromContract();
        if (source == null || source.getEndDate() == null) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "Renewal source contract is missing");
        }
        if (source.getStatus() != ContractStatus.ACTIVE
                || !source.isActive() || source.isDeleted()) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_NOT_ALLOWED,
                    "The source contract is no longer active");
        }
        if (LocalDate.now(BUSINESS_ZONE).isAfter(source.getEndDate())) {
            throw new BadRequestException(ErrorCode.CONTRACT_RENEWAL_DEADLINE_PASSED,
                    "The renewal deadline has passed");
        }
    }

    private boolean isRenewalEligible(RentalContract sourceContract, User owner) {
        if (sourceContract.getRenewedFromContract() != null
                || sourceContract.getStatus() != ContractStatus.ACTIVE
                || !sourceContract.isActive()
                || sourceContract.isDeleted()
                || owner == null
                || sourceContract.getOwner() == null
                || !owner.getId().equals(sourceContract.getOwner().getId())
                || sourceContract.getTenant() == null
                || sourceContract.getWarehouse() == null) {
            return false;
        }

        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (sourceContract.getStartDate() == null || sourceContract.getEndDate() == null
                || today.isBefore(sourceContract.getStartDate())
                || today.isAfter(sourceContract.getEndDate())) {
            return false;
        }

        Warehouse warehouse = sourceContract.getWarehouse();
        User tenant = sourceContract.getTenant();
        if (!warehouse.isActive() || warehouse.isDeleted()
                || !tenant.isActive() || tenant.isDeleted()) {
            return false;
        }
        if (contractRepository.findBlockingRenewalsBySourceId(sourceContract.getId()).size() > 0) {
            return false;
        }
        if (warehouseLayoutService.findActiveTenantLayoutForContract(
                warehouse.getId(), tenant.getId()).isEmpty()) {
            return false;
        }

        RentalPricingType warehousePricingType = warehouse.getRentalPricingType() != null
                ? warehouse.getRentalPricingType()
                : RentalPricingType.FIXED_MONTHLY;
        if (sourceContract.getPricingType() != warehousePricingType) {
            return false;
        }
        WarehouseLayoutResponse defaultLayout = warehouseLayoutService
                .getDefaultLayoutForContract(warehouse.getId());
        try {
            RentalAreaAllocationPolicy.LeasedDimensions dimensions = RentalAreaAllocationPolicy.validatePreservedDimensions(
                    sourceContract.getPricingType(),
                    sourceContract.getLeasedWidth(),
                    sourceContract.getLeasedLength(),
                    sourceContract.getLeasedHeight(),
                    defaultLayout);
            if (sourceContract.getLeasedAreaM2() == null
                    || sourceContract.getLeasedAreaM2().compareTo(dimensions.areaM2()) != 0) {
                return false;
            }
        } catch (BadRequestException exception) {
            return false;
        }
        return true;
    }

    private record ActionFlags(boolean canEdit,
                               boolean canDelete,
                               boolean canSubmit,
                               boolean canConfirm,
                               boolean canRequestChanges,
                               boolean canReject,
                               boolean canViewLayout,
                               boolean canManageWms,
                               boolean layoutSetupRequired,
                               boolean canEditContractLayout,
                               boolean canCreateRenewal) {
        private static final ActionFlags NONE = new ActionFlags(
                false, false, false, false, false, false, false, false, false, false, false);
    }




}

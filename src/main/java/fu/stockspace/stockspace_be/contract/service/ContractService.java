package fu.stockspace.stockspace_be.contract.service;
import java.util.UUID;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.util.TenantContextUtil;
import fu.stockspace.stockspace_be.booking.entity.BookingRequest;

import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.dto.*;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.DisputeTicket;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.dto.BulkLayoutSaveRequest;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;








@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {
    private static final long MIN_RENTAL_DURATION_DAYS = 7;

    private final RentalContractRepository contractRepository;
    private final BookingRequestRepository bookingRepository;
    private final WarehouseService warehouseService;
    private final DisputeTicketRepository disputeRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final WarehouseLayoutService warehouseLayoutService;
    private final NotificationService notificationService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;






    @Transactional
    public RentalContract createContractFromBooking(UUID bookingId) {
        BookingRequest booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BOOKING_NOT_FOUND));
        RentalContract contract = RentalContract.builder()
                .booking(booking)
                .status(ContractStatus.UNDER_NEGOTIATION)
                .startDate(null)
                .endDate(null)
                .tenantConfirmed(false)
                .ownerConfirmed(false)
                .build();
        contract = contractRepository.save(contract);
        log.info("RentalContract created: {} in UNDER_NEGOTIATION state for booking {}", contract.getId(), bookingId);
        return contract;
    }




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
        UUID tenantId = contract.getEffectiveTenant() != null ? contract.getEffectiveTenant().getId() : null;
        UUID ownerId = contract.getEffectiveOwner() != null ? contract.getEffectiveOwner().getId() : null;
        if (!userId.equals(tenantId) && !userId.equals(ownerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return mapToResponse(contract, userId);
    }

    @Transactional(readOnly = true)
    public RentalContractResponse previewOwnerDraft(UUID ownerId, CreateRentalContractRequest request) {
        DraftTerms terms = resolveDraftTerms(ownerId, request);
        RentalContract draft = buildDraftContract(terms, request);
        return mapToResponse(draft);
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
            throw new BadRequestException("Only DRAFT or CHANGES_REQUESTED contracts can be edited");
        }
        if (request == null) {
            throw new BadRequestException("Contract update request is required");
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

    /**
     * Revalidates and submits a direct contract without relying on Booking.
     * The warehouse row is locked before the overlap query so two concurrent
     * submissions for the same warehouse observe a serialized state.
     */
    @Transactional
    public RentalContractResponse submitOwnerContract(UUID ownerId, UUID contractId) {
        RentalContract contract = findDirectContractForOwnerEdit(ownerId, contractId);
        if (contract.getStatus() != ContractStatus.DRAFT
                && contract.getStatus() != ContractStatus.CHANGES_REQUESTED) {
            throw new BadRequestException("Only DRAFT or CHANGES_REQUESTED contracts can be submitted");
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
            throw new BadRequestException(
                    "The tenant already has an overlapping contract for this warehouse");
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
        contract.setTenantConfirmed(false);
        contract.setOwnerConfirmed(false);
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
        return mapToResponse(contract, ownerId);
    }

    private RentalContract findDirectContractForOwnerEdit(UUID ownerId, UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (!contract.isActive() || contract.isDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        if (contract.getOwner() == null || contract.getTenant() == null || contract.getWarehouse() == null) {
            throw new BadRequestException("This operation requires a direct rental contract");
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
        String files = contract.getPaperContractFiles() != null
                ? contract.getPaperContractFiles()
                : contract.getPaperContractImages();
        if (files == null || files.isBlank()) {
            throw new BadRequestException("At least one paper contract file is required before submission");
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
                throw new BadRequestException("At least one valid paper contract file is required before submission");
            }
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Paper contract files must be valid JSON");
        }
    }

    private void notifyTenantOfSubmission(RentalContract contract, User tenant, Warehouse warehouse) {
        try {
            notificationService.push(
                    tenant.getId(),
                    "Rental contract requires confirmation",
                    "The owner submitted a rental contract for warehouse " + warehouse.getName() + ".",
                    "CONTRACT");
        } catch (Exception e) {
            log.warn("Failed to push direct contract notification for {}: {}",
                    contract.getId(), e.getMessage());
        }
    }

    /**
     * Confirms a direct contract submitted by its owner. This lifecycle is
     * deliberately independent from the legacy Booking and wallet/deposit
     * flow: tenant confirmation only activates the contract and does not
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
            throw new BadRequestException(
                    "The tenant already has an overlapping contract for this warehouse");
        }

        contract.setTenantConfirmed(true);
        contract.setConfirmedAt(LocalDateTime.now());
        contract.setStatus(ContractStatus.ACTIVE);
        contract = contractRepository.save(contract);

        notifyOwnerOfTenantDecision(
                contract,
                "Rental contract confirmed",
                "The tenant confirmed the rental contract for warehouse "
                        + lockedWarehouse.getName() + ".");
        return mapToResponse(contract, tenantId);
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
        contract.setTenantConfirmed(false);
        contract.setConfirmedAt(null);
        contract.setStatus(ContractStatus.CHANGES_REQUESTED);
        contract = contractRepository.save(contract);

        notifyOwnerOfTenantDecision(
                contract,
                "Rental contract changes requested",
                "The tenant requested changes to the rental contract for warehouse "
                        + contract.getWarehouse().getName() + ". Reason: " + reason);
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
        contract.setTenantConfirmed(false);
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
                        + contract.getWarehouse().getName() + ". Reason: " + reason);
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
            throw new BadRequestException("This operation requires a direct rental contract");
        }
        if (!tenantId.equals(contract.getTenant().getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return contract;
    }

    private void requirePendingTenantConfirmation(RentalContract contract) {
        if (contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException(
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
                                             String message) {
        try {
            notificationService.push(
                    contract.getOwner().getId(), title, message, "CONTRACT");
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
            throw new BadRequestException("Contract layout can only be edited in DRAFT or CHANGES_REQUESTED");
        }
        validateContractLayoutDimensions(contract, request);

        Warehouse warehouse = contract.getEffectiveWarehouse();
        User tenant = contract.getEffectiveTenant();
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
        User tenant = contract.getEffectiveTenant();
        if (tenant == null || !tenantId.equals(tenant.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (!isTenantLayoutReadable(contract.getStatus())) {
            throw new BadRequestException("Contract layout is not available in the current contract state");
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
        User owner = contract.getEffectiveOwner();
        if (owner == null || !ownerId.equals(owner.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
    }

    private WarehouseLayoutResponse getCurrentOrSnapshotLayout(RentalContract contract) {
        Warehouse warehouse = contract.getEffectiveWarehouse();
        User tenant = contract.getEffectiveTenant();
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
            return objectMapper.readValue(snapshot, WarehouseLayoutResponse.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Contract layout snapshot is invalid");
        }
    }

    private void validateContractLayoutDimensions(RentalContract contract,
                                                   BulkLayoutSaveRequest request) {
        if (request == null || request.getWidth() == null || request.getLength() == null
                || request.getHeight() == null) {
            throw new BadRequestException("Contract layout dimensions are required");
        }
        if (contract.getLeasedWidth() == null || contract.getLeasedLength() == null
                || contract.getLeasedHeight() == null
                || request.getWidth().compareTo(contract.getLeasedWidth()) != 0
                || request.getLength().compareTo(contract.getLeasedLength()) != 0
                || request.getHeight().compareTo(contract.getLeasedHeight()) != 0) {
            throw new BadRequestException("Contract layout dimensions cannot be changed");
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
            throw new BadRequestException("Only DRAFT contracts can be deleted");
        }
        User owner = contract.getEffectiveOwner();
        if (owner == null || !ownerId.equals(owner.getId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        contract.setActive(false);
        contract.setDeleted(true);
        contractRepository.save(contract);

        Warehouse warehouse = contract.getEffectiveWarehouse();
        User tenant = contract.getEffectiveTenant();
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
        User tenant = userRepository.findActiveByEmailAndRole(
                        request.getTenantEmail().trim(), RoleType.ROLE_TENANT.name())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND,
                        "Active tenant account was not found for the supplied email"));

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
            throw new BadRequestException("Tenant account must be active");
        }
        if (!warehouse.isActive() || warehouse.isDeleted()
                || !warehouse.isVerified()
                || warehouse.getStatus() == WarehouseStatus.INACTIVE) {
            throw new BadRequestException("Warehouse must be verified and active");
        }

        RentalPricingType pricingType = warehouse.getRentalPricingType() != null
                ? warehouse.getRentalPricingType()
                : RentalPricingType.FIXED_MONTHLY;
        WarehouseLayoutResponse defaultLayout = warehouseLayoutService
                .getDefaultLayoutForContract(warehouse.getId());
        validateLeasedDimensions(
                leasedWidth, leasedLength, leasedHeight, defaultLayout, pricingType);
        BigDecimal rentalPrice = warehouse.getRentalPrice() != null
                ? warehouse.getRentalPrice()
                : warehouse.getPricePerMonth();
        BigDecimal area = leasedWidth.multiply(leasedLength);
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
                leasedWidth,
                leasedLength,
                leasedHeight,
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

    private void validateLeasedDimensions(BigDecimal width,
                                           BigDecimal length,
                                           BigDecimal height,
                                           WarehouseLayoutResponse defaultLayout,
                                           RentalPricingType pricingType) {
        if (width == null || length == null || height == null
                || width.signum() <= 0 || length.signum() <= 0 || height.signum() <= 0) {
            throw new BadRequestException("Leased dimensions must be greater than 0");
        }
        if (defaultLayout.getWidth() == null || defaultLayout.getLength() == null
                || defaultLayout.getHeight() == null) {
            throw new BadRequestException("Warehouse default layout dimensions are incomplete");
        }
        if (width.compareTo(defaultLayout.getWidth()) > 0
                || length.compareTo(defaultLayout.getLength()) > 0
                || height.compareTo(defaultLayout.getHeight()) > 0) {
            throw new BadRequestException("Leased dimensions cannot exceed the warehouse default layout");
        }

        if (pricingType == RentalPricingType.FIXED_MONTHLY
                && (defaultLayout.getWidth().compareTo(width) != 0
                || defaultLayout.getLength().compareTo(length) != 0
                || defaultLayout.getHeight().compareTo(height) != 0)) {
            throw new BadRequestException(
                    "FIXED_MONTHLY contracts must use the complete default layout dimensions");
        }
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










    @Transactional
    public RentalContractResponse confirmHandover(UUID userId, UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        if (contract.getStatus() != ContractStatus.PENDING_HANDOVER) {
            throw new BadRequestException("Chỉ được xác nhận bàn giao khi hợp đồng đang ở trạng thái chờ bàn giao");
        }
        UUID tenantId = contract.getBooking().getTenant().getId();
        UUID ownerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (userId.equals(tenantId)) {
            if (contract.isTenantConfirmed()) {
                throw new BadRequestException(ErrorCode.CONTRACT_ALREADY_CONFIRMED);
            }
            contract.setTenantConfirmed(true);
            log.info("Tenant {} confirmed handover for contract {}", userId, contractId);
        } else if (userId.equals(ownerId)) {
            if (contract.isOwnerConfirmed()) {
                throw new BadRequestException(ErrorCode.CONTRACT_ALREADY_CONFIRMED);
            }
            contract.setOwnerConfirmed(true);
            log.info("Owner {} confirmed handover for contract {}", userId, contractId);
        } else {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        if (contract.isTenantConfirmed() && contract.isOwnerConfirmed()) {
            contract.setStatus(ContractStatus.COMPLETED);
            warehouseService.markAsAvailable(contract.getBooking().getWarehouse().getId());
            log.info("Contract {} COMPLETED — warehouse {} is now AVAILABLE",
                    contractId, contract.getBooking().getWarehouse().getId());
        } else {
            contract.setStatus(ContractStatus.PENDING_HANDOVER);
        }
        contract = contractRepository.save(contract);
        return mapToResponse(contract);
    }




    @Transactional
    public void setDisputed(UUID contractId) {
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        contract.setStatus(ContractStatus.DISPUTED);
        contractRepository.save(contract);
    }

    public RentalContractResponse mapToResponse(RentalContract c) {
        return mapToResponse(c, null);
    }

    public RentalContractResponse mapToResponse(RentalContract c, UUID viewerId) {
        BookingRequest b = c.getBooking();
        var tenant = c.getEffectiveTenant();
        var warehouse = c.getEffectiveWarehouse();
        var owner = c.getEffectiveOwner();
        if (owner == null && warehouse != null) {
            owner = warehouse.getOwner();
        }
        String paperContractFiles = c.getPaperContractFiles() != null
                ? c.getPaperContractFiles()
                : c.getPaperContractImages();
        ActionFlags actionFlags = calculateActionFlags(c, viewerId, tenant, owner);
        return RentalContractResponse.builder()
                .id(c.getId())
                .status(c.getStatus().name())
                .tenantConfirmed(c.isTenantConfirmed())
                .ownerConfirmed(c.isOwnerConfirmed())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .paperContractFiles(paperContractFiles)
                .paperContractImages(paperContractFiles)
                .bookingId(b != null ? b.getId() : null)
                .depositAmount(b != null ? b.getDepositAmount() : null)
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
                .pricingType(c.getPricingType())
                .rentalPriceSnapshot(c.getRentalPriceSnapshot())
                .finalMonthlyRent(c.getFinalMonthlyRent())
                .leasedWidth(c.getLeasedWidth())
                .leasedLength(c.getLeasedLength())
                .leasedHeight(c.getLeasedHeight())
                .leasedAreaM2(c.getLeasedAreaM2())
                .ownerNote(c.getOwnerNote())
                .layoutSnapshot(c.getLayoutSnapshot())
                .changeRequestReason(c.getChangeRequestReason())
                .rejectionReason(c.getRejectionReason())
                .confirmedAt(c.getConfirmedAt())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .submittedAt(c.getSubmittedAt())
                .cancelReason(c.getCancelReason())
                .cancelEvidence(c.getCancelEvidence())
                .build();
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
        boolean ownerCanEdit = ownerViewer
                && (status == ContractStatus.DRAFT || status == ContractStatus.CHANGES_REQUESTED);
        boolean tenantCanReview = tenantViewer && status == ContractStatus.PENDING_TENANT_CONFIRM;
        boolean tenantCanViewLayout = tenantViewer && isTenantLayoutReadable(status);
        boolean canManageWms = tenantViewer
                && status == ContractStatus.ACTIVE
                && subscriptionService != null
                && subscriptionService.hasActiveSubscription(tenant.getId());
        return new ActionFlags(
                ownerCanEdit,
                ownerViewer && status == ContractStatus.DRAFT,
                ownerCanEdit,
                tenantCanReview,
                tenantCanReview,
                tenantCanReview,
                (ownerViewer && contract.isActive() && !contract.isDeleted()) || tenantCanViewLayout,
                canManageWms);
    }

    private record ActionFlags(boolean canEdit,
                               boolean canDelete,
                               boolean canSubmit,
                               boolean canConfirm,
                               boolean canRequestChanges,
                               boolean canReject,
                               boolean canViewLayout,
                               boolean canManageWms) {
        private static final ActionFlags NONE = new ActionFlags(
                false, false, false, false, false, false, false, false);
    }




    @Transactional
    public RentalContractResponse submitOnlineContract(UUID ownerId, UUID contractId, SubmitContractRequest request) {
        log.info("Owner {} submitting online contract for contract {}", ownerId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualOwnerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (!ownerId.equals(actualOwnerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.UNDER_NEGOTIATION) {
            throw new BadRequestException("Hợp đồng không ở trạng thái thương lượng");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BadRequestException("Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) < 7) {
            throw new BadRequestException("Thời gian thuê kho tối thiểu là 7 ngày");
        }
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        try {
            contract.setPaperContractFiles(objectMapper.writeValueAsString(request.getPaperContractFiles()));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Paper contract files must be valid JSON");
        }
        contract.setStatus(ContractStatus.PENDING_TENANT_CONFIRM);
        contract.setSubmittedAt(LocalDateTime.now());
        contract = contractRepository.save(contract);
        log.info("Contract {} status updated to PENDING_TENANT_CONFIRM", contractId);


        try {
            UUID tenantId = contract.getBooking().getTenant().getId();
            String warehouseName = contract.getBooking().getWarehouse().getName();
            notificationService.push(
                    tenantId,
                    "Hợp đồng cần xác nhận",
                    "Owner đã cập nhật hợp đồng kho " + warehouseName + ". Bạn có 7 ngày để ký xác nhận.",
                    "CONTRACT"
            );
        } catch (Exception e) {
            log.warn("Failed to push contract notification to tenant: {}", e.getMessage());
        }

        return mapToResponse(contract);
    }



    @Transactional
    public RentalContractResponse tenantConfirmContract(UUID tenantId, UUID contractId) {
        log.info("Tenant {} confirming contract {}", tenantId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualTenantId = contract.getBooking().getTenant().getId();
        if (!tenantId.equals(actualTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException("Hợp đồng không ở trạng thái chờ xác nhận");
        }

        if (contract.getSubmittedAt() != null && contract.getSubmittedAt().plusDays(7).isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Hợp đồng đã quá hạn 7 ngày để xác nhận. Tiền cọc đã bị xử lý.");
        }
        contract.setTenantConfirmed(true);
        contract.setOwnerConfirmed(true);
        contract.setStatus(ContractStatus.ACTIVE);

        try {
            warehouseLayoutService.cloneLayout(contract.getBooking().getWarehouse().getId(), actualTenantId);
        } catch (Exception e) {
            log.error("Failed to auto-clone layout for tenant {} after contract activation: {}", actualTenantId, e.getMessage());
        }


        BigDecimal depositAmount = contract.getBooking().getDepositAmount();
        if (depositAmount == null) {
            depositAmount = BigDecimal.ZERO;
        }
        walletService.refundBalance(
            contract.getBooking().getWarehouse().getOwner().getId(),
            depositAmount,
            TransactionType.DEPOSIT_RECEIVED,
            "Nhận cọc thuê kho: " + contract.getBooking().getWarehouse().getName(),
            contract.getBooking().getId(),
            null
        );

        contract = contractRepository.save(contract);
        log.info("Contract {} is now ACTIVE", contractId);
        return mapToResponse(contract);
    }



    @Transactional
    public RentalContractResponse tenantReportFailed(UUID tenantId, UUID contractId, TenantReportFailedRequest request) {
        log.info("Tenant {} reporting contract failed: {}", tenantId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualTenantId = contract.getBooking().getTenant().getId();
        if (!tenantId.equals(actualTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.UNDER_NEGOTIATION
                && contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException("Không thể báo cáo sự cố hợp đồng ở trạng thái hiện tại");
        }

        if (disputeRepository.findByContractId(contract.getId()).isPresent()) {
            throw new BadRequestException("Hợp đồng này đã có tranh chấp đang mở");
        }
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        String evidenceJson = request.getEvidenceImages() != null ? request.getEvidenceImages().toString() : null;
        DisputeTicket ticket = DisputeTicket.builder()
                .contract(contract)
                .raisedBy(tenant)
                .reason(request.getReason())
                .evidenceImages(evidenceJson)
                .status("OPEN")
                .build();
        disputeRepository.save(ticket);
        contract.setStatus(ContractStatus.DISPUTED);
        contract = contractRepository.save(contract);
        log.info("Dispute ticket opened for contract {} by Tenant", contractId);
        return mapToResponse(contract);
    }



    @Transactional
    public RentalContractResponse ownerRequestCancel(UUID ownerId, UUID contractId, OwnerCancelRequest request) {
        log.info("Owner {} requesting cancellation for contract {}", ownerId, contractId);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualOwnerId = contract.getBooking().getWarehouse().getOwner().getId();
        if (!ownerId.equals(actualOwnerId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.UNDER_NEGOTIATION
                && contract.getStatus() != ContractStatus.PENDING_TENANT_CONFIRM) {
            throw new BadRequestException("Không thể đề xuất hủy hợp đồng ở trạng thái hiện tại");
        }
        contract.setCancelReason(request.getReason());
        String evidenceJson = request.getEvidenceImages() != null ? request.getEvidenceImages().toString() : null;
        contract.setCancelEvidence(evidenceJson);
        contract.setStatus(ContractStatus.PENDING_CANCEL);
        contract = contractRepository.save(contract);
        log.info("Contract {} is now PENDING_CANCEL", contractId);
        return mapToResponse(contract);
    }



    @Transactional
    public RentalContractResponse tenantRespondCancel(UUID tenantId, UUID contractId, boolean agree) {
        log.info("Tenant {} responding to cancel request for contract {}, agree={}", tenantId, contractId, agree);
        RentalContract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND));
        UUID actualTenantId = contract.getBooking().getTenant().getId();
        if (!tenantId.equals(actualTenantId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        if (contract.getStatus() != ContractStatus.PENDING_CANCEL) {
            throw new BadRequestException("Hợp đồng không có yêu cầu hủy nào đang chờ phản hồi");
        }
        if (agree) {

            contract.setStatus(ContractStatus.CANCELLED);
            warehouseService.markAsAvailable(contract.getBooking().getWarehouse().getId());








            walletService.refundBalance(
                tenantId,
                contract.getBooking().getDepositAmount(),
                TransactionType.DEPOSIT_REFUND,
                "Hoàn đặt cọc thuê kho do hai bên đồng thuận hủy: " + contract.getBooking().getWarehouse().getName(),
                contract.getBooking().getId(),
                null
            );


            contract.getBooking().setStatus(fu.stockspace.stockspace_be.booking.entity.ApprovalStatus.CANCELLED);
            contract.getBooking().setRejectReason("Hợp đồng bị hủy do hai bên đồng thuận hủy");
            bookingRepository.save(contract.getBooking());

            log.info("Contract {} cancelled by mutual agreement", contractId);
        } else {

            if (disputeRepository.findByContractId(contract.getId()).isPresent()) {
                throw new BadRequestException("Hợp đồng này đã có tranh chấp đang mở");
            }
            User tenant = userRepository.findById(tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
            DisputeTicket ticket = DisputeTicket.builder()
                    .contract(contract)
                    .raisedBy(tenant)
                    .reason("Tenant không đồng ý yêu cầu hủy thương lượng của Owner. Lý do hủy của Owner: " + contract.getCancelReason())
                    .evidenceImages(contract.getCancelEvidence())
                    .status("OPEN")
                    .build();
            disputeRepository.save(ticket);
            contract.setStatus(ContractStatus.DISPUTED);
            log.info("Tenant disagreed to cancel. Contract {} status changed to DISPUTED", contractId);

            try {
                String warehouseName = contract.getBooking().getWarehouse().getName();
                UUID ownerId = contract.getBooking().getWarehouse().getOwner().getId();

                // 1. Thông báo cho Owner rằng Tenant không đồng ý hủy và đã mở tranh chấp
                notificationService.push(
                        ownerId,
                        "Khách thuê từ chối yêu cầu hủy",
                        "Khách thuê không đồng ý yêu cầu hủy hợp đồng kho '" + warehouseName + "'. Vụ việc đã được chuyển thành tranh chấp để Ban quản trị phân xử.",
                        "DISPUTE"
                );

                // 2. Thông báo cho Admin hệ thống
                userRepository.findFirstByRoles_Name(RoleType.ROLE_ADMIN.name())
                        .ifPresent(admin -> notificationService.push(
                                admin.getId(),
                                "Tranh chấp hợp đồng mới",
                                "Có một tranh chấp mới phát sinh từ việc từ chối hủy hợp đồng kho '" + warehouseName + "'. Vui lòng kiểm tra và phân xử.",
                                "DISPUTE"
                        ));
            } catch (Exception e) {
                log.warn("Failed to push dispute notification: {}", e.getMessage());
            }
        }
        contract = contractRepository.save(contract);
        return mapToResponse(contract);
    }
}

package fu.stockspace.stockspace_be.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.contract.dto.CreateRentalContractRequest;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseLayoutResponse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractDraftServiceTest {

    @Mock private RentalContractRepository contractRepository;
    @Mock private BookingRequestRepository bookingRepository;
    @Mock private WarehouseService warehouseService;
    @Mock private DisputeTicketRepository disputeRepository;
    @Mock private UserRepository userRepository;
    @Mock private WalletService walletService;
    @Mock private WarehouseLayoutService warehouseLayoutService;
    @Mock private NotificationService notificationService;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ContractService contractService;

    private UUID ownerId;
    private UUID tenantId;
    private UUID warehouseId;
    private User tenant;
    private Warehouse warehouse;
    private WarehouseLayoutResponse defaultLayout;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();

        User owner = User.builder().id(ownerId).fullName("Owner").build();
        tenant = User.builder()
                .id(tenantId)
                .email("tenant@example.com")
                .fullName("Tenant")
                .roles(Set.of(Role.builder().name(RoleType.ROLE_TENANT.name()).build()))
                .build();
        warehouse = Warehouse.builder()
                .id(warehouseId)
                .owner(owner)
                .name("Warehouse A")
                .status(WarehouseStatus.AVAILABLE)
                .isVerified(true)
                .rentalPricingType(RentalPricingType.FIXED_MONTHLY)
                .rentalPrice(new BigDecimal("1000000"))
                .build();
        defaultLayout = WarehouseLayoutResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .isDefault(true)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .positions(List.of())
                .build();

    }

    @Test
    void previewFixedUsesTheCompleteDefaultLayoutAndDoesNotPersist() {
        stubDraftValidation();
        CreateRentalContractRequest request = request("10", "20", "5");

        RentalContractResponse response = contractService.previewOwnerDraft(ownerId, request);

        assertEquals(ContractStatus.DRAFT.name(), response.getStatus());
        assertEquals(new BigDecimal("1000000"), response.getFinalMonthlyRent());
        assertEquals(new BigDecimal("200"), response.getLeasedAreaM2());
        assertEquals(ownerId, response.getOwnerId());
        assertEquals(tenantId, response.getTenantId());
        assertNull(response.getBookingId());
        verify(contractRepository, never()).save(any(RentalContract.class));
        verify(warehouseLayoutService, never()).prepareTenantLayoutForDraft(
                any(), any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void previewPerSquareMeterCalculatesFinalRentFromTheRequestedArea() {
        stubDraftValidation();
        warehouse.setRentalPricingType(RentalPricingType.PER_SQUARE_METER_MONTHLY);
        warehouse.setRentalPrice(new BigDecimal("200000"));
        CreateRentalContractRequest request = request("4", "5", "5");

        RentalContractResponse response = contractService.previewOwnerDraft(ownerId, request);

        assertEquals(RentalPricingType.PER_SQUARE_METER_MONTHLY, response.getPricingType());
        assertEquals(new BigDecimal("4000000"), response.getFinalMonthlyRent());
        assertEquals(new BigDecimal("200000"), response.getRentalPriceSnapshot());
        assertEquals(new BigDecimal("20"), response.getLeasedAreaM2());
    }

    @Test
    void previewNegotiatedRequiresAndUsesManualRent() {
        stubDraftValidation();
        warehouse.setRentalPricingType(RentalPricingType.NEGOTIATED);
        CreateRentalContractRequest request = request("4", "5", "5");
        request.setNegotiatedMonthlyRent(new BigDecimal("3500000"));

        RentalContractResponse response = contractService.previewOwnerDraft(ownerId, request);

        assertEquals(RentalPricingType.NEGOTIATED, response.getPricingType());
        assertEquals(new BigDecimal("3500000"), response.getFinalMonthlyRent());
        assertNull(response.getRentalPriceSnapshot());
    }

    @Test
    void createDraftStoresDirectRelationsAndPreparedLayoutSnapshot() throws Exception {
        stubDraftValidation();
        CreateRentalContractRequest request = request("10", "20", "5");
        request.setOwnerNote("Paper contract signed outside the platform");
        request.setPaperContractFiles(List.of("https://example.com/contract.pdf"));
        WarehouseLayoutResponse tenantLayout = WarehouseLayoutResponse.builder()
                .warehouseId(warehouseId)
                .tenantId(tenantId)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("20"))
                .height(new BigDecimal("5"))
                .racks(List.of())
                .positions(List.of())
                .build();
        when(warehouseLayoutService.prepareTenantLayoutForDraft(
                warehouseId, tenantId, new BigDecimal("10"), new BigDecimal("20"), new BigDecimal("5"), true))
                .thenReturn(tenantLayout);
        when(contractRepository.save(any(RentalContract.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RentalContractResponse response = contractService.createOwnerDraft(ownerId, request);

        assertEquals(ContractStatus.DRAFT.name(), response.getStatus());
        assertEquals("Paper contract signed outside the platform", response.getOwnerNote());
        assertNull(response.getBookingId());
        org.mockito.ArgumentCaptor<RentalContract> captor = org.mockito.ArgumentCaptor.forClass(RentalContract.class);
        verify(contractRepository).save(captor.capture());
        RentalContract saved = captor.getValue();
        assertEquals(ownerId, saved.getOwner().getId());
        assertEquals(tenantId, saved.getTenant().getId());
        assertEquals(warehouseId, saved.getWarehouse().getId());
        assertNull(saved.getBooking());
        assertEquals("[\"https://example.com/contract.pdf\"]", saved.getPaperContractFiles());
        objectMapper.readTree(saved.getLayoutSnapshot());
    }

    @Test
    void fixedPricingRejectsDimensionsThatDoNotMatchTheDefaultLayout() {
        stubDraftValidation();
        CreateRentalContractRequest request = request("9", "20", "5");

        assertThrows(BadRequestException.class, () -> contractService.previewOwnerDraft(ownerId, request));
        verify(contractRepository, never()).save(any(RentalContract.class));
    }

    @Test
    void previewRejectsWarehouseNotOwnedByTheCurrentOwner() {
        when(warehouseService.getOwnedWarehouseForContract(ownerId, warehouseId))
                .thenThrow(new ForbiddenException("Warehouse is not owned by the current owner"));

        assertThrows(ForbiddenException.class,
                () -> contractService.previewOwnerDraft(ownerId, request("10", "20", "5")));
        verify(userRepository, never()).findActiveByEmailAndRole(any(), any());
    }

    @Test
    void previewRejectsEmailThatDoesNotResolveToAnActiveTenant() {
        when(warehouseService.getOwnedWarehouseForContract(ownerId, warehouseId)).thenReturn(warehouse);
        when(userRepository.findActiveByEmailAndRole("tenant@example.com", RoleType.ROLE_TENANT.name()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> contractService.previewOwnerDraft(ownerId, request("10", "20", "5")));
        verify(warehouseLayoutService, never()).getDefaultLayoutForContract(warehouseId);
    }

    @Test
    void deleteDraftSoftDeletesAndArchivesLayoutWhenNoActiveContractRemains() {
        RentalContract draft = RentalContract.builder()
                .id(UUID.randomUUID())
                .owner(warehouse.getOwner())
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.DRAFT)
                .build();
        when(contractRepository.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId))
                .thenReturn(false);

        contractService.deleteOwnerDraft(ownerId, draft.getId());

        assertEquals(false, draft.isActive());
        assertEquals(true, draft.isDeleted());
        verify(contractRepository).save(draft);
        verify(warehouseLayoutService).archiveTenantLayout(warehouseId, tenantId);
    }

    @Test
    void deleteRejectsNonDraftContract() {
        RentalContract active = RentalContract.builder()
                .id(UUID.randomUUID())
                .owner(warehouse.getOwner())
                .tenant(tenant)
                .warehouse(warehouse)
                .status(ContractStatus.ACTIVE)
                .build();
        when(contractRepository.findById(active.getId())).thenReturn(Optional.of(active));

        assertThrows(BadRequestException.class, () -> contractService.deleteOwnerDraft(ownerId, active.getId()));
        verify(contractRepository, never()).save(any(RentalContract.class));
        verify(warehouseLayoutService, never()).archiveTenantLayout(any(), any());
    }

    private CreateRentalContractRequest request(String width, String length, String height) {
        CreateRentalContractRequest request = new CreateRentalContractRequest();
        request.setWarehouseId(warehouseId);
        request.setTenantEmail("tenant@example.com");
        request.setStartDate(LocalDate.now());
        request.setEndDate(LocalDate.now().plusDays(7));
        request.setLeasedWidth(new BigDecimal(width));
        request.setLeasedLength(new BigDecimal(length));
        request.setLeasedHeight(new BigDecimal(height));
        return request;
    }

    private void stubDraftValidation() {
        when(warehouseService.getOwnedWarehouseForContract(ownerId, warehouseId)).thenReturn(warehouse);
        when(userRepository.findActiveByEmailAndRole("tenant@example.com", RoleType.ROLE_TENANT.name()))
                .thenReturn(Optional.of(tenant));
        when(warehouseLayoutService.getDefaultLayoutForContract(warehouseId)).thenReturn(defaultLayout);
    }
}

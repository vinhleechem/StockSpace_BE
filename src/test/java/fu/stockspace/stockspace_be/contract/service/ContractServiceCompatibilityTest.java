package fu.stockspace.stockspace_be.contract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.booking.entity.BookingRequest;
import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.dto.SubmitContractRequest;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.contract.entity.RentalContract;
import fu.stockspace.stockspace_be.contract.repository.DisputeTicketRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseLayoutService;
import fu.stockspace.stockspace_be.warehouse.service.WarehouseService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.repository.BookingRequestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
class ContractServiceCompatibilityTest {

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

    @Test
    void mapToResponseReadsDirectContractWithoutBooking() {
        User owner = User.builder().id(UUID.randomUUID()).fullName("Owner").build();
        User tenant = User.builder().id(UUID.randomUUID()).fullName("Tenant").email("tenant@example.com").build();
        Warehouse warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Warehouse A")
                .address("District 1")
                .owner(owner)
                .build();
        RentalContract contract = RentalContract.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .tenant(tenant)
                .warehouse(warehouse)
                .pricingType(RentalPricingType.PER_SQUARE_METER_MONTHLY)
                .rentalPriceSnapshot(new BigDecimal("200000"))
                .finalMonthlyRent(new BigDecimal("16000000"))
                .leasedWidth(new BigDecimal("10"))
                .leasedLength(new BigDecimal("8"))
                .leasedAreaM2(new BigDecimal("80"))
                .status(ContractStatus.DRAFT)
                .build();

        RentalContractResponse response = contractService.mapToResponse(contract);

        assertEquals(owner.getId(), response.getOwnerId());
        assertEquals(tenant.getId(), response.getTenantId());
        assertEquals(warehouse.getId(), response.getWarehouseId());
        assertEquals(RentalPricingType.PER_SQUARE_METER_MONTHLY, response.getPricingType());
        assertEquals(new BigDecimal("16000000"), response.getFinalMonthlyRent());
        assertNull(response.getBookingId());
        assertNull(response.getDepositAmount());
    }

    @Test
    void mapToResponseFallsBackToLegacyBookingRelations() {
        User owner = User.builder().id(UUID.randomUUID()).fullName("Owner").build();
        User tenant = User.builder().id(UUID.randomUUID()).fullName("Tenant").email("tenant@example.com").build();
        Warehouse warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Legacy Warehouse")
                .address("District 2")
                .owner(owner)
                .build();
        BookingRequest booking = BookingRequest.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .warehouse(warehouse)
                .depositAmount(new BigDecimal("500000"))
                .build();
        RentalContract contract = RentalContract.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .status(ContractStatus.PENDING_TENANT_CONFIRM)
                .build();

        RentalContractResponse response = contractService.mapToResponse(contract);

        assertEquals(booking.getId(), response.getBookingId());
        assertEquals(booking.getDepositAmount(), response.getDepositAmount());
        assertEquals(owner.getId(), response.getOwnerId());
        assertEquals(tenant.getId(), response.getTenantId());
        assertEquals(warehouse.getId(), response.getWarehouseId());
    }

    @Test
    void submitOnlineContractStoresPaperFilesAsValidJson() throws Exception {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).fullName("Owner").build();
        User tenant = User.builder().id(UUID.randomUUID()).fullName("Tenant").build();
        Warehouse warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Warehouse A")
                .owner(owner)
                .build();
        BookingRequest booking = BookingRequest.builder()
                .id(UUID.randomUUID())
                .tenant(tenant)
                .warehouse(warehouse)
                .build();
        RentalContract contract = RentalContract.builder()
                .id(UUID.randomUUID())
                .booking(booking)
                .status(ContractStatus.UNDER_NEGOTIATION)
                .build();
        SubmitContractRequest request = new SubmitContractRequest();
        request.setStartDate(LocalDate.of(2026, 9, 1));
        request.setEndDate(LocalDate.of(2026, 9, 8));
        request.setPaperContractFiles(List.of("https://example.com/paper.pdf"));

        org.mockito.Mockito.when(contractRepository.findById(contract.getId()))
                .thenReturn(java.util.Optional.of(contract));
        org.mockito.Mockito.when(contractRepository.save(contract)).thenReturn(contract);

        contractService.submitOnlineContract(ownerId, contract.getId(), request);

        assertEquals("[\"https://example.com/paper.pdf\"]", contract.getPaperContractFiles());
        org.junit.jupiter.api.Assertions.assertTrue(
                objectMapper.readTree(contract.getPaperContractFiles()).isArray());
    }
}

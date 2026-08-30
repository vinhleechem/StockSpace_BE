package fu.stockspace.stockspace_be.listing.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.listing.dto.PurchaseListingPackageRequest;
import fu.stockspace.stockspace_be.listing.entity.ListingOrder;
import fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus;
import fu.stockspace.stockspace_be.listing.entity.ListingPackage;
import fu.stockspace.stockspace_be.listing.repository.ListingOrderRepository;
import fu.stockspace.stockspace_be.listing.repository.ListingPackageRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingOrderServiceTest {

    private static final ZoneId PUBLICATION_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-08-31T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 31);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 8, 0);

    @Mock
    private ListingOrderRepository listingOrderRepository;

    @Mock
    private ListingPackageRepository listingPackageRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private fu.stockspace.stockspace_be.wallet.repository.TransactionRepository transactionRepository;

    @Mock
    private WarehouseLayoutRepository warehouseLayoutRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private Clock publicationClock;

    @InjectMocks
    private ListingOrderService listingOrderService;

    private UUID ownerId;
    private UUID warehouseId;
    private UUID packageId;
    private User owner;
    private Warehouse warehouse;
    private ListingPackage listingPackage;
    private WarehouseLayout defaultLayout;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        packageId = UUID.randomUUID();
        owner = User.builder().id(ownerId).fullName("Warehouse Owner").build();
        warehouse = Warehouse.builder()
                .id(warehouseId)
                .owner(owner)
                .name("Warehouse A")
                .status(WarehouseStatus.AVAILABLE)
                .isActive(true)
                .isDeleted(false)
                .build();
        listingPackage = ListingPackage.builder()
                .id(packageId)
                .name("Listing Package - 10 Days")
                .durationDays(10)
                .price(new BigDecimal("50000.00"))
                .isActive(true)
                .isDeleted(false)
                .build();
        defaultLayout = WarehouseLayout.builder()
                .warehouse(warehouse)
                .isDefault(true)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .isActive(true)
                .isDeleted(false)
                .build();

        lenient().when(publicationClock.instant()).thenReturn(FIXED_INSTANT);
        lenient().when(publicationClock.getZone()).thenReturn(PUBLICATION_ZONE);
        lenient().when(warehouseLayoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId))
                .thenReturn(Optional.of(defaultLayout));
        lenient().when(listingOrderRepository.findOpenPaidByWarehouseIdForUpdate(warehouseId, NOW))
                .thenReturn(List.of());
    }

    @Test
    void purchaseTodayCreatesPaidOrderAndPublishesImmediately() {
        Transaction transaction = successfulTransaction();
        stubPurchaseDependencies(transaction);

        var response = listingOrderService.purchasePublication(
                ownerId,
                warehouseId,
                request(TODAY));

        assertEquals(ListingOrderStatus.PAID, response.getStatus());
        assertEquals(NOW, response.getPeriodStart());
        assertEquals(NOW.plusDays(10), response.getPeriodEnd());
        assertEquals(NOW, warehouse.getPublishedAt());
        assertEquals(NOW.plusDays(10), warehouse.getVisibleUntil());
        verify(walletService).deductBalance(
                eq(ownerId), eq(listingPackage.getPrice()), eq(TransactionType.LISTING_FEE),
                any(String.class), eq(null), eq(null));
        verify(notificationService).push(
                eq(ownerId), eq("Warehouse published"), any(String.class), eq("LISTING_PUBLISHED"));
        assertEquals(transaction.getId(), response.getTransactionId());
    }

    @Test
    void purchaseFutureDateCreatesPaidScheduledOrderWithoutEarlyPublication() {
        Transaction transaction = successfulTransaction();
        stubPurchaseDependencies(transaction);
        LocalDate futureDate = TODAY.plusDays(2);

        var response = listingOrderService.purchasePublication(
                ownerId,
                warehouseId,
                request(futureDate));

        LocalDateTime expectedStart = futureDate.atStartOfDay();
        assertEquals(ListingOrderStatus.PAID, response.getStatus());
        assertEquals(expectedStart, response.getPeriodStart());
        assertEquals(expectedStart.plusDays(10), response.getPeriodEnd());
        assertEquals(expectedStart, warehouse.getPublishedAt());
        verify(notificationService).push(
                eq(ownerId), eq("Warehouse publication scheduled"), any(String.class), eq("LISTING_SCHEDULED"));
        assertEquals(transaction.getId(), response.getTransactionId());
    }

    @Test
    void purchaseRejectsPastStartDateBeforeCreatingOrderOrChargingWallet() {
        stubWarehouseAndPackage();

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> listingOrderService.purchasePublication(ownerId, warehouseId, request(TODAY.minusDays(1))));

        assertTrue(exception.getMessage().contains("past"));
        verify(listingOrderRepository, never()).save(any(ListingOrder.class));
        verifyNoInteractions(walletService, transactionRepository);
    }

    @Test
    void purchaseRejectsWarehouseThatIsNotApproved() {
        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> listingOrderService.purchasePublication(ownerId, warehouseId, request(TODAY)));

        assertEquals(ErrorCode.WAREHOUSE_NOT_AVAILABLE, exception.getErrorCode());
        verify(listingPackageRepository, never()).findById(any());
        verifyNoInteractions(walletService, transactionRepository);
    }

    @Test
    void purchaseRejectsWhenAnotherPaidPeriodIsOpen() {
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.findOpenPaidByWarehouseIdForUpdate(warehouseId, NOW))
                .thenReturn(List.of(ListingOrder.builder().id(UUID.randomUUID()).build()));

        ResourceConflictException exception = assertThrows(
                ResourceConflictException.class,
                () -> listingOrderService.purchasePublication(ownerId, warehouseId, request(TODAY)));

        assertEquals(ErrorCode.LISTING_PUBLICATION_PENDING, exception.getErrorCode());
        verify(listingOrderRepository, never()).save(any(ListingOrder.class));
        verifyNoInteractions(walletService, transactionRepository);
    }

    @Test
    void purchaseRejectsInactivePackageBeforeChargingWallet() {
        stubWarehouseAndPackage();
        listingPackage.setActive(false);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> listingOrderService.purchasePublication(ownerId, warehouseId, request(TODAY)));

        assertEquals(ErrorCode.LISTING_PACKAGE_INACTIVE, exception.getErrorCode());
        verify(listingOrderRepository, never()).save(any(ListingOrder.class));
        verifyNoInteractions(walletService, transactionRepository);
    }

    @Test
    void insufficientBalanceDoesNotPersistWarehousePublicationMetadata() {
        stubWarehouseAndPackage();
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(any(), any(), eq(TransactionType.LISTING_FEE), any(), eq(null), eq(null)))
                .thenThrow(new BadRequestException(ErrorCode.INSUFFICIENT_BALANCE));

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> listingOrderService.purchasePublication(ownerId, warehouseId, request(TODAY)));

        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
        assertNull(warehouse.getPublishedAt());
        assertNull(warehouse.getVisibleUntil());
        verify(warehouseRepository, never()).save(warehouse);
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void purchaseRejectsWarehouseOwnedByAnotherUser() {
        warehouse.setOwner(User.builder().id(UUID.randomUUID()).build());
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> listingOrderService.purchasePublication(ownerId, warehouseId, request(TODAY)));

        assertEquals(ErrorCode.WAREHOUSE_NOT_OWNED, exception.getErrorCode());
        verify(listingPackageRepository, never()).findById(any());
    }

    private void stubPurchaseDependencies(Transaction transaction) {
        stubWarehouseAndPackage();
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(
                eq(ownerId), eq(listingPackage.getPrice()), eq(TransactionType.LISTING_FEE),
                any(String.class), eq(null), eq(null))).thenReturn(transaction);
    }

    private void stubWarehouseAndPackage() {
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
    }

    private PurchaseListingPackageRequest request(LocalDate startDate) {
        return new PurchaseListingPackageRequest(packageId, startDate);
    }

    private Transaction successfulTransaction() {
        return Transaction.builder().id(UUID.randomUUID()).build();
    }
}

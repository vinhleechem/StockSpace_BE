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
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.listing.repository.ListingOrderRepository;
import fu.stockspace.stockspace_be.listing.repository.ListingPackageRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingOrderServiceTest {

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
                .isVerified(true)
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
        lenient().when(warehouseLayoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId))
                .thenReturn(Optional.of(defaultLayout));
        lenient().when(listingOrderRepository.existsByWarehouseIdAndStatusAndIsDeletedFalse(
                warehouseId, ListingOrderStatus.PENDING_APPROVAL)).thenReturn(false);
    }

    @Test
    void purchaseCreatesOrderTransactionAndPublicationPeriod() {
        Transaction transaction = Transaction.builder().id(UUID.randomUUID()).build();
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(
                eq(ownerId), eq(listingPackage.getPrice()), eq(TransactionType.LISTING_FEE),
                any(String.class), eq(null), eq(null)
        )).thenReturn(transaction);

        var response = listingOrderService.purchaseOrRenew(
                ownerId, warehouseId, new PurchaseListingPackageRequest(packageId));

        assertNotNull(response.getId());
        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals(packageId, response.getListingPackageId());
        assertEquals(transaction.getId(), response.getTransactionId());
        assertEquals(ListingOrderStatus.ACTIVATED, response.getStatus());
        assertEquals(response.getPeriodStart().plusDays(10), response.getPeriodEnd());
        assertEquals(response.getPeriodStart(), warehouse.getPublishedAt());
        assertEquals(response.getPeriodEnd(), warehouse.getVisibleUntil());
        verify(transactionRepository).save(transaction);
        verify(warehouseRepository).save(warehouse);
        verify(notificationService).push(eq(ownerId), any(String.class), any(String.class), eq("LISTING_PUBLISHED"));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 15, 30})
    void purchaseAcceptsEverySupportedListingDuration(int durationDays) {
        listingPackage.setDurationDays(durationDays);
        Transaction transaction = Transaction.builder().id(UUID.randomUUID()).build();
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(
                eq(ownerId), eq(listingPackage.getPrice()), eq(TransactionType.LISTING_FEE),
                any(String.class), eq(null), eq(null)
        )).thenReturn(transaction);

        var response = listingOrderService.purchaseOrRenew(
                ownerId, warehouseId, new PurchaseListingPackageRequest(packageId));

        assertEquals(durationDays, response.getDurationDays());
        assertEquals(response.getPeriodStart().plusDays(durationDays), response.getPeriodEnd());
        assertEquals(response.getPeriodEnd(), warehouse.getVisibleUntil());
    }

    @Test
    void purchaseExtendsFromCurrentVisibleUntilWithoutLosingRemainingDays() {
        LocalDateTime currentVisibleUntil = LocalDateTime.now().plusDays(5);
        LocalDateTime publishedAt = LocalDateTime.now().minusDays(5);
        warehouse.setPublishedAt(publishedAt);
        warehouse.setVisibleUntil(currentVisibleUntil);
        listingPackage.setDurationDays(15);
        Transaction transaction = Transaction.builder().id(UUID.randomUUID()).build();

        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(any(), any(), eq(TransactionType.LISTING_FEE), any(), eq(null), eq(null)))
                .thenReturn(transaction);

        var response = listingOrderService.purchaseOrRenew(
                ownerId, warehouseId, new PurchaseListingPackageRequest(packageId));

        assertEquals(currentVisibleUntil, response.getPeriodStart());
        assertEquals(currentVisibleUntil.plusDays(15), response.getPeriodEnd());
        assertEquals(publishedAt, warehouse.getPublishedAt());
    }

    @Test
    void sequentialRenewalsExtendFromTheLatestLockedPublicationPeriod() {
        Transaction transaction = Transaction.builder().id(UUID.randomUUID()).build();
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(
                eq(ownerId), eq(listingPackage.getPrice()), eq(TransactionType.LISTING_FEE),
                any(String.class), eq(null), eq(null)
        )).thenReturn(transaction);

        var firstOrder = listingOrderService.purchaseOrRenew(
                ownerId, warehouseId, new PurchaseListingPackageRequest(packageId));
        var firstPeriodEnd = firstOrder.getPeriodEnd();

        var secondOrder = listingOrderService.purchaseOrRenew(
                ownerId, warehouseId, new PurchaseListingPackageRequest(packageId));

        assertEquals(firstPeriodEnd, secondOrder.getPeriodStart());
        assertEquals(firstPeriodEnd.plusDays(10), secondOrder.getPeriodEnd());
        assertEquals(secondOrder.getPeriodEnd(), warehouse.getVisibleUntil());
        verify(warehouseRepository, times(2)).findByIdForUpdate(warehouseId);
        verify(warehouseRepository, never()).findById(warehouseId);
    }

    @Test
    void purchaseAfterExpiryStartsFromNow() {
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(20));
        warehouse.setVisibleUntil(LocalDateTime.now().minusDays(1));
        Transaction transaction = Transaction.builder().id(UUID.randomUUID()).build();
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(any(), any(), eq(TransactionType.LISTING_FEE), any(), eq(null), eq(null)))
                .thenReturn(transaction);

        LocalDateTime before = LocalDateTime.now();
        var response = listingOrderService.purchaseOrRenew(
                ownerId, warehouseId, new PurchaseListingPackageRequest(packageId));

        assertEquals(response.getPeriodStart(), warehouse.getPublishedAt());
        assertEquals(response.getPeriodEnd(), warehouse.getVisibleUntil());
        assertEquals(response.getPeriodStart().plusDays(10), response.getPeriodEnd());
        assertEquals(true, response.getPeriodStart().isAfter(before.minusSeconds(1)));
    }

    @Test
    void purchaseRejectsInactiveListingPackage() {
        listingPackage.setActive(false);
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> listingOrderService.purchaseOrRenew(
                        ownerId, warehouseId, new PurchaseListingPackageRequest(packageId)));
        assertEquals(ErrorCode.LISTING_PACKAGE_INACTIVE, exception.getErrorCode());
        verify(walletService, never()).deductBalance(any(), any(), any(), any(), any(), any());
    }

    @Test
    void purchaseAcceptsUnverifiedWarehouseWhileAwaitingAdminApproval() {
        warehouse.setVerified(false);
        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        Transaction transaction = Transaction.builder().id(UUID.randomUUID()).build();
        when(walletService.deductBalance(
                eq(ownerId), eq(listingPackage.getPrice()), eq(TransactionType.LISTING_FEE),
                any(String.class), eq(null), eq(null)
        )).thenReturn(transaction);

        var response = listingOrderService.purchaseOrRenew(
                ownerId, warehouseId, new PurchaseListingPackageRequest(packageId));

        assertEquals(ListingOrderStatus.PENDING_APPROVAL, response.getStatus());
        assertNull(response.getPeriodStart());
        assertNull(response.getPeriodEnd());
        verify(warehouseRepository, never()).save(warehouse);
        verify(notificationService).push(eq(ownerId),
                eq("Warehouse publication awaiting approval"), any(String.class),
                eq("LISTING_PENDING_APPROVAL"));
    }

    @Test
    void purchaseRejectsWhenDefaultLayoutIsMissingBeforeChargingWallet() {
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(warehouseLayoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId))
                .thenReturn(Optional.empty());

        var exception = assertThrows(
                fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException.class,
                () -> listingOrderService.purchaseOrRenew(
                        ownerId, warehouseId, new PurchaseListingPackageRequest(packageId)));

        assertEquals(ErrorCode.WAREHOUSE_DEFAULT_LAYOUT_REQUIRED, exception.getErrorCode());
        verify(walletService, never()).deductBalance(any(), any(), any(), any(), any(), any());
        verify(listingOrderRepository, never()).save(any(ListingOrder.class));
    }

    @Test
    void purchaseRejectsDuplicatePendingPublicationBeforeChargingWallet() {
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingOrderRepository.existsByWarehouseIdAndStatusAndIsDeletedFalse(
                warehouseId, ListingOrderStatus.PENDING_APPROVAL)).thenReturn(true);

        ResourceConflictException exception = assertThrows(
                ResourceConflictException.class,
                () -> listingOrderService.purchaseOrRenew(
                        ownerId, warehouseId, new PurchaseListingPackageRequest(packageId)));

        assertEquals(ErrorCode.LISTING_PUBLICATION_PENDING, exception.getErrorCode());
        verify(listingPackageRepository, never()).findById(any());
        verify(walletService, never()).deductBalance(any(), any(), any(), any(), any(), any());
        verify(listingOrderRepository, never()).save(any(ListingOrder.class));
    }

    @Test
    void purchaseRejectsWarehouseOwnedByAnotherUser() {
        warehouse.setOwner(User.builder().id(UUID.randomUUID()).build());
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> listingOrderService.purchaseOrRenew(
                        ownerId, warehouseId, new PurchaseListingPackageRequest(packageId)));
        assertEquals(ErrorCode.WAREHOUSE_NOT_OWNED, exception.getErrorCode());
        verify(listingPackageRepository, never()).findById(any());
    }

    @Test
    void insufficientBalanceDoesNotPersistPublicationMetadata() {
        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingPackageRepository.findById(packageId)).thenReturn(Optional.of(listingPackage));
        when(listingOrderRepository.save(any(ListingOrder.class))).thenAnswer(invocation -> {
            ListingOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            return order;
        });
        when(walletService.deductBalance(any(), any(), eq(TransactionType.LISTING_FEE), any(), eq(null), eq(null)))
                .thenThrow(new BadRequestException(ErrorCode.INSUFFICIENT_BALANCE));

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> listingOrderService.purchaseOrRenew(
                        ownerId, warehouseId, new PurchaseListingPackageRequest(packageId)));
        assertEquals(ErrorCode.INSUFFICIENT_BALANCE, exception.getErrorCode());
        verify(transactionRepository, never()).save(any());
        verify(warehouseRepository, never()).save(warehouse);
    }

    @Test
    void publicationHistoryReturnsOrdersForOwnedWarehouseWithTransactionReference() {
        ListingOrder order = ListingOrder.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .warehouse(warehouse)
                .listingPackage(listingPackage)
                .durationDaysSnapshot(10)
                .priceSnapshot(listingPackage.getPrice())
                .periodStart(LocalDateTime.now())
                .periodEnd(LocalDateTime.now().plusDays(10))
                .build();
        Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .listingOrderId(order.getId())
                .build();
        Transaction refund = Transaction.builder()
                .id(UUID.randomUUID())
                .listingOrderId(order.getId())
                .build();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingOrderRepository.findAllByOwnerIdAndWarehouseId(ownerId, warehouseId))
                .thenReturn(java.util.List.of(order));
        when(transactionRepository.findAllByListingOrderIdInAndTransactionType(
                eq(java.util.List.of(order.getId())), eq(TransactionType.LISTING_FEE)))
                .thenReturn(java.util.List.of(transaction));
        when(transactionRepository.findAllByListingOrderIdInAndTransactionType(
                eq(java.util.List.of(order.getId())), eq(TransactionType.LISTING_REFUND)))
                .thenReturn(java.util.List.of(refund));

        var response = listingOrderService.getPublicationHistory(ownerId, warehouseId);

        assertEquals(1, response.size());
        assertEquals(transaction.getId(), response.get(0).getTransactionId());
        assertEquals(refund.getId(), response.get(0).getRefundTransactionId());
        assertEquals(10, response.get(0).getDurationDays());
    }

    @Test
    void publicationHistoryRejectsAnotherOwnersWarehouse() {
        warehouse.setOwner(User.builder().id(UUID.randomUUID()).build());
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertThrows(ForbiddenException.class, () -> listingOrderService.getPublicationHistory(ownerId, warehouseId));
        verify(listingOrderRepository, never()).findAllByOwnerIdAndWarehouseId(any(), any());
    }
}

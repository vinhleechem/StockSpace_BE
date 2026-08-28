package fu.stockspace.stockspace_be.warehouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.dto.PagedResponse;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.listing.entity.ListingOrder;
import fu.stockspace.stockspace_be.listing.entity.ListingOrderStatus;
import fu.stockspace.stockspace_be.listing.repository.ListingOrderRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseOwnerContactResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseSearchRequest;
import fu.stockspace.stockspace_be.warehouse.dto.UpdateWarehouseRequest;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseLayout;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseLayoutRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionStatus;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private TenantWarehouseAccessService tenantWarehouseAccessService;

    @Mock
    private ListingOrderRepository listingOrderRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private WarehouseLayoutRepository warehouseLayoutRepository;

    @Mock
    private WalletService walletService;

    @InjectMocks
    private WarehouseService warehouseService;

    private UUID warehouseId;
    private UUID ownerId;
    private Warehouse warehouse;
    private User owner;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        ownerId = UUID.randomUUID();

        owner = User.builder()
                .id(ownerId)
                .fullName("Owner Test")
                .phone("0987654321")
                .build();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Kho Test")
                .status(WarehouseStatus.PENDING_APPROVAL)
                .owner(owner)
                .images(new ArrayList<>())
                .build();
    }

    @Test
    void getActiveContractWarehousesUsesCurrentDirectContractAccess() {
        when(tenantWarehouseAccessService.findActiveContractWarehouses(ownerId))
                .thenReturn(List.of(warehouse));

        List<WarehouseResponse> responses = warehouseService.getActiveContractWarehouses(ownerId);

        assertEquals(1, responses.size());
        assertEquals(warehouseId, responses.get(0).getId());
        verify(tenantWarehouseAccessService).findActiveContractWarehouses(ownerId);
    }

    @Test
    void rejectWarehouse_WithReason_Success() {
        String reason = "Kho không đủ giấy phép PCCC";
        ListingOrder order = ListingOrder.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .warehouse(warehouse)
                .durationDaysSnapshot(10)
                .priceSnapshot(new BigDecimal("50000"))
                .status(ListingOrderStatus.PENDING_APPROVAL)
                .build();
        Transaction payment = Transaction.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("50000"))
                .transactionType(TransactionType.LISTING_FEE)
                .status(TransactionStatus.SUCCESS)
                .listingOrderId(order.getId())
                .build();
        Transaction refund = Transaction.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("50000"))
                .transactionType(TransactionType.LISTING_REFUND)
                .status(TransactionStatus.SUCCESS)
                .build();

        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingOrderRepository.findPendingByWarehouseIdForUpdate(warehouseId))
                .thenReturn(List.of(order));
        when(transactionRepository.findByListingOrderIdAndTransactionType(
                order.getId(), TransactionType.LISTING_FEE)).thenReturn(Optional.of(payment));
        when(walletService.refundBalance(
                eq(ownerId), eq(new BigDecimal("50000")), eq(TransactionType.LISTING_REFUND),
                any(String.class), eq(null), eq(null))).thenReturn(refund);
        when(listingOrderRepository.save(any(ListingOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.rejectWarehouse(warehouseId, reason);

        assertNotNull(response);
        assertEquals(WarehouseStatus.INACTIVE.name(), response.getStatus());
        assertEquals(reason, response.getRejectReason());
        assertEquals(ListingOrderStatus.REFUNDED, order.getStatus());
        assertNull(warehouse.getPublishedAt());
        assertNull(warehouse.getVisibleUntil());
        verify(walletService).refundBalance(
                eq(ownerId), eq(new BigDecimal("50000")), eq(TransactionType.LISTING_REFUND),
                any(String.class), eq(null), eq(null));

        verify(notificationService).push(
                eq(ownerId),
                eq("Bài đăng kho bãi không được duyệt"),
                contains(reason),
                eq("SYSTEM")
        );
    }

    @Test
    void approveWarehouse_ClearsRejectReasonAndActivatesPaidPublication() {
        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        warehouse.setRejectReason("Lý do cũ");
        ListingOrder order = ListingOrder.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .warehouse(warehouse)
                .durationDaysSnapshot(10)
                .priceSnapshot(new BigDecimal("50000"))
                .status(ListingOrderStatus.PENDING_APPROVAL)
                .build();
        Transaction payment = Transaction.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("50000"))
                .transactionType(TransactionType.LISTING_FEE)
                .status(TransactionStatus.SUCCESS)
                .listingOrderId(order.getId())
                .build();
        WarehouseLayout defaultLayout = WarehouseLayout.builder()
                .warehouse(warehouse)
                .isDefault(true)
                .width(new BigDecimal("10"))
                .length(new BigDecimal("10"))
                .height(new BigDecimal("5"))
                .build();

        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingOrderRepository.findPendingByWarehouseIdForUpdate(warehouseId))
                .thenReturn(List.of(order));
        when(transactionRepository.findByListingOrderIdAndTransactionType(
                order.getId(), TransactionType.LISTING_FEE)).thenReturn(Optional.of(payment));
        when(warehouseLayoutRepository.findByWarehouseIdAndIsDefaultTrue(warehouseId))
                .thenReturn(Optional.of(defaultLayout));
        when(listingOrderRepository.save(any(ListingOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.approveWarehouse(warehouseId);

        assertNotNull(response);
        assertEquals(WarehouseStatus.AVAILABLE.name(), response.getStatus());
        assertNull(response.getRejectReason());
        assertEquals(ListingOrderStatus.ACTIVATED, order.getStatus());
        assertNotNull(order.getPeriodStart());
        assertNotNull(order.getPeriodEnd());
        assertNotNull(warehouse.getPublishedAt());
        assertNotNull(warehouse.getVisibleUntil());
    }

    @Test
    void resubmitRejectedWarehouseReturnsToPendingApprovalWithoutChargingWallet() {
        warehouse.setStatus(WarehouseStatus.INACTIVE);
        warehouse.setRejectReason("Thiếu thông tin hồ sơ");
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(1));
        warehouse.setVisibleUntil(LocalDateTime.now().plusDays(5));

        ListingOrder refundedOrder = ListingOrder.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .warehouse(warehouse)
                .durationDaysSnapshot(10)
                .priceSnapshot(new BigDecimal("50000"))
                .status(ListingOrderStatus.REFUNDED)
                .build();

        when(warehouseRepository.findByIdForUpdate(warehouseId)).thenReturn(Optional.of(warehouse));
        when(listingOrderRepository.findAllByOwnerIdAndWarehouseId(ownerId, warehouseId))
                .thenReturn(List.of(refundedOrder));
        when(warehouseRepository.save(any(Warehouse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.resubmitWarehouse(ownerId, warehouseId);

        assertEquals(WarehouseStatus.PENDING_APPROVAL.name(), response.getStatus());
        assertNull(response.getRejectReason());
        assertNull(warehouse.getPublishedAt());
        assertNull(warehouse.getVisibleUntil());
        verify(walletService, never()).refundBalance(any(), any(), any(), any(), any(), any());
        verify(notificationService).push(
                eq(ownerId),
                eq("Warehouse listing resubmitted"),
                any(String.class),
                eq("LISTING_RESUBMITTED"));
    }

    @Test
    void updateWarehouse_AllowsNegotiatedPricingWithoutNumericRentalPrice() {
        warehouse.setRentalPricingType(RentalPricingType.FIXED_MONTHLY);
        warehouse.setRentalPrice(new BigDecimal("15000000"));

        UpdateWarehouseRequest request = new UpdateWarehouseRequest();
        request.setRentalPricingType(RentalPricingType.NEGOTIATED);

        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any(Warehouse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.updateWarehouse(ownerId, warehouseId, request);

        assertEquals(RentalPricingType.NEGOTIATED, response.getRentalPricingType());
        assertNull(response.getRentalPrice());
    }

    @Test
    void updateWarehouseRejectsNonPositiveRentalPrice() {
        UpdateWarehouseRequest request = new UpdateWarehouseRequest();
        request.setRentalPricingType(RentalPricingType.FIXED_MONTHLY);
        request.setRentalPrice(BigDecimal.ZERO);

        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));

        assertThrows(BadRequestException.class,
                () -> warehouseService.updateWarehouse(ownerId, warehouseId, request));
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void updateWarehouseRejectsIncompleteStructuredLocation() {
        UpdateWarehouseRequest request = new UpdateWarehouseRequest();
        request.setProvinceCode("79");

        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));

        assertThrows(BadRequestException.class,
                () -> warehouseService.updateWarehouse(ownerId, warehouseId, request));
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void updateWarehouseStoresCompleteStructuredLocation() {
        UpdateWarehouseRequest request = new UpdateWarehouseRequest();
        request.setProvinceCode("79");
        request.setProvinceName("Thành phố Hồ Chí Minh");
        request.setDistrictCode("760");
        request.setDistrictName("Quận 9");

        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any(Warehouse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.updateWarehouse(ownerId, warehouseId, request);

        assertEquals("79", warehouse.getProvinceCode());
        assertEquals("Thành phố Hồ Chí Minh", warehouse.getProvinceName());
        assertEquals("760", warehouse.getDistrictCode());
        assertEquals("Quận 9", warehouse.getDistrictName());
        assertEquals("79", response.getProvinceCode());
        assertEquals("760", response.getDistrictCode());
    }

    @Test
    void updateWarehouseClearsNormalizedLocationWhenAddressChangesWithoutStructuredLocation() {
        warehouse.setProvinceCode("79");
        warehouse.setProvinceName("Thành phố Hồ Chí Minh");
        warehouse.setDistrictCode("760");
        warehouse.setDistrictName("Quận 9");

        UpdateWarehouseRequest request = new UpdateWarehouseRequest();
        request.setAddress("Địa chỉ mới");

        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any(Warehouse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        warehouseService.updateWarehouse(ownerId, warehouseId, request);

        assertNull(warehouse.getProvinceCode());
        assertNull(warehouse.getProvinceName());
        assertNull(warehouse.getDistrictCode());
        assertNull(warehouse.getDistrictName());
    }

    @Test
    void searchWarehousesForwardsStructuredFiltersToPublicQuery() {
        UUID warehouseTypeId = UUID.randomUUID();
        WarehouseSearchRequest request = new WarehouseSearchRequest();
        request.setKeyword("Kho khô");
        request.setMinRentalPrice(new BigDecimal("1000000"));
        request.setMaxRentalPrice(new BigDecimal("5000000"));
        request.setMinCapacity(new BigDecimal("50"));
        request.setMaxCapacity(new BigDecimal("200"));
        request.setProvinceCode("79");
        request.setDistrictCode("760");
        request.setWarehouseTypeId(warehouseTypeId);
        request.setIsVerified(true);

        when(warehouseRepository.searchPublic(
                eq("%kho khô%"),
                eq(WarehouseStatus.AVAILABLE),
                eq(new BigDecimal("1000000")),
                eq(new BigDecimal("5000000")),
                eq(new BigDecimal("50")),
                eq(new BigDecimal("200")),
                eq("79"),
                eq("760"),
                eq(warehouseTypeId),
                eq(true),
                any()
        )).thenReturn(new PageImpl<>(List.of(warehouse)));

        PagedResponse<WarehouseResponse> result = warehouseService
                .searchWarehouses(request, 0, 10, "createdAt", "desc");

        assertEquals(1, result.getTotalElements());
        verify(warehouseRepository).searchPublic(
                eq("%kho khô%"),
                eq(WarehouseStatus.AVAILABLE),
                eq(new BigDecimal("1000000")),
                eq(new BigDecimal("5000000")),
                eq(new BigDecimal("50")),
                eq(new BigDecimal("200")),
                eq("79"),
                eq("760"),
                eq(warehouseTypeId),
                eq(true),
                any()
        );
    }

    @Test
    void authenticatedContactRequestReturnsOwnerPhoneForVerifiedActiveWarehouse() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(true);
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(1));
        warehouse.setVisibleUntil(LocalDateTime.now().plusDays(10));
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.of(warehouse));

        WarehouseOwnerContactResponse response = warehouseService.getOwnerContact(warehouseId);

        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals(ownerId, response.getOwnerId());
        assertEquals("Owner Test", response.getOwnerName());
        assertEquals("0987654321", response.getPhone());
    }

    @Test
    void contactRequestRejectsInactiveWarehouse() {
        warehouse.setStatus(WarehouseStatus.INACTIVE);
        warehouse.setVerified(true);
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> warehouseService.getOwnerContact(warehouseId));
    }

    @Test
    void contactRequestAllowsUnverifiedPublishedWarehouse() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(false);
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(1));
        warehouse.setVisibleUntil(LocalDateTime.now().plusDays(10));
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.of(warehouse));

        WarehouseOwnerContactResponse response = warehouseService.getOwnerContact(warehouseId);

        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals(ownerId, response.getOwnerId());
    }

    @Test
    void contactRequestRejectsInactiveOrDeletedWarehouseRecord() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(true);
        warehouse.setActive(false);
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> warehouseService.getOwnerContact(warehouseId));

        warehouse.setActive(true);
        warehouse.setDeleted(true);

        assertThrows(ResourceNotFoundException.class,
                () -> warehouseService.getOwnerContact(warehouseId));
    }

    @Test
    void contactRequestRejectsMissingWarehouse() {
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> warehouseService.getOwnerContact(warehouseId));
    }

    @Test
    void publicWarehouseResponseDoesNotExposeOwnerPhone() throws Exception {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(true);
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(1));
        warehouse.setVisibleUntil(LocalDateTime.now().plusDays(10));
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.of(warehouse));

        WarehouseResponse response = warehouseService.getWarehouseDetail(warehouseId);
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);

        assertFalse(json.contains("ownerPhone"));
        assertFalse(json.contains("0987654321"));
    }

    @Test
    void publicWarehouseDetailRejectsExpiredPublication() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(true);
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(20));
        warehouse.setVisibleUntil(LocalDateTime.now().minusDays(1));
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> warehouseService.getWarehouseDetail(warehouseId));
    }

    @Test
    void ownerWarehouseResponseIncludesPublicationStatusAndActionFlags() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(true);
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(1));
        warehouse.setVisibleUntil(LocalDateTime.now().plusDays(10));
        when(warehouseRepository.findByOwnerId(eq(ownerId), any()))
                .thenReturn(new PageImpl<>(List.of(warehouse)));

        WarehouseResponse response = warehouseService
                .getMyWarehouses(ownerId, 0, 10, "createdAt", "desc")
                .getContent()
                .get(0);

        assertEquals("PUBLISHED", response.getPublicationStatus());
        assertFalse(response.isCanPublish());
        assertTrue(response.isCanRenew());
    }

    @Test
    void expiredWarehouseCanBeRenewedButCannotBeInitiallyPublished() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(true);
        warehouse.setPublishedAt(LocalDateTime.now().minusDays(20));
        warehouse.setVisibleUntil(LocalDateTime.now().minusDays(1));
        when(warehouseRepository.findByOwnerId(eq(ownerId), any()))
                .thenReturn(new PageImpl<>(List.of(warehouse)));

        WarehouseResponse response = warehouseService
                .getMyWarehouses(ownerId, 0, 10, "createdAt", "desc")
                .getContent()
                .get(0);

        assertEquals("EXPIRED", response.getPublicationStatus());
        assertFalse(response.isCanPublish());
        assertTrue(response.isCanRenew());
    }

    @Test
    void unpublishedWarehouseCanBePublishedButCannotBeRenewed() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(true);
        when(warehouseRepository.findByOwnerId(eq(ownerId), any()))
                .thenReturn(new PageImpl<>(List.of(warehouse)));

        WarehouseResponse response = warehouseService
                .getMyWarehouses(ownerId, 0, 10, "createdAt", "desc")
                .getContent()
                .get(0);

        assertEquals("DRAFT", response.getPublicationStatus());
        assertTrue(response.isCanPublish());
        assertFalse(response.isCanRenew());
    }

    @Test
    void pendingPaidListingExposesPendingStateAndDisablesActions() {
        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        ListingOrderRepository.LatestListingOrderState state = mock(ListingOrderRepository.LatestListingOrderState.class);
        UUID orderId = UUID.randomUUID();

        when(warehouseRepository.findByOwnerId(eq(ownerId), any()))
                .thenReturn(new PageImpl<>(List.of(warehouse)));
        when(listingOrderRepository.findLatestStateByWarehouseIds(List.of(warehouseId)))
                .thenReturn(List.of(state));
        when(state.getWarehouseId()).thenReturn(warehouseId);
        when(state.getOrderId()).thenReturn(orderId);
        when(state.getStatus()).thenReturn(ListingOrderStatus.PENDING_APPROVAL);

        WarehouseResponse response = warehouseService
                .getMyWarehouses(ownerId, 0, 10, "createdAt", "desc")
                .getContent()
                .get(0);

        assertEquals("PENDING_APPROVAL", response.getPublicationStatus());
        assertEquals(orderId, response.getCurrentListingOrderId());
        assertEquals(ListingOrderStatus.PENDING_APPROVAL, response.getCurrentListingOrderStatus());
        assertFalse(response.isCanPublish());
        assertFalse(response.isCanRenew());
    }

    @Test
    void rejectedRefundedListingExposesRefundedStateUntilResubmission() {
        warehouse.setStatus(WarehouseStatus.INACTIVE);
        warehouse.setRejectReason("Thiếu giấy tờ");
        ListingOrderRepository.LatestListingOrderState state = mock(ListingOrderRepository.LatestListingOrderState.class);
        UUID orderId = UUID.randomUUID();

        when(warehouseRepository.findByOwnerId(eq(ownerId), any()))
                .thenReturn(new PageImpl<>(List.of(warehouse)));
        when(listingOrderRepository.findLatestStateByWarehouseIds(List.of(warehouseId)))
                .thenReturn(List.of(state));
        when(state.getWarehouseId()).thenReturn(warehouseId);
        when(state.getOrderId()).thenReturn(orderId);
        when(state.getStatus()).thenReturn(ListingOrderStatus.REFUNDED);

        WarehouseResponse response = warehouseService
                .getMyWarehouses(ownerId, 0, 10, "createdAt", "desc")
                .getContent()
                .get(0);

        assertEquals("REFUNDED", response.getPublicationStatus());
        assertEquals(orderId, response.getCurrentListingOrderId());
        assertEquals(ListingOrderStatus.REFUNDED, response.getCurrentListingOrderStatus());
        assertFalse(response.isCanPublish());
        assertFalse(response.isCanRenew());
    }

    @Test
    void deleteWarehouseIsBlockedByAnActiveContractInsteadOfListingStatus() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));
        when(warehouseRepository.hasCurrentActiveContract(warehouseId)).thenReturn(true);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> warehouseService.deleteWarehouse(ownerId, warehouseId));

        assertEquals("Không thể xoá kho đang có hợp đồng thuê hiệu lực", exception.getMessage());
        verify(warehouseRepository, never()).save(any(Warehouse.class));
    }

    @Test
    void deleteWarehouseSucceedsWhenNoActiveContractExists() {
        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));
        when(warehouseRepository.hasCurrentActiveContract(warehouseId)).thenReturn(false);

        warehouseService.deleteWarehouse(ownerId, warehouseId);

        assertTrue(warehouse.isDeleted());
        verify(warehouseRepository).save(warehouse);
    }
}

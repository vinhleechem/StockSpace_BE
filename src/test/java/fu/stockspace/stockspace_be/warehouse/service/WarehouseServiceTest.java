package fu.stockspace.stockspace_be.warehouse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseOwnerContactResponse;
import fu.stockspace.stockspace_be.warehouse.dto.UpdateWarehouseRequest;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.RentalPricingType;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
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
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.rejectWarehouse(warehouseId, reason);

        assertNotNull(response);
        assertEquals(WarehouseStatus.INACTIVE.name(), response.getStatus());
        assertEquals(reason, response.getRejectReason());

        verify(notificationService).push(
                eq(ownerId),
                eq("Bài đăng kho bãi không được duyệt"),
                contains(reason),
                eq("SYSTEM")
        );
    }

    @Test
    void verifyWarehouse_ClearsRejectReason_Success() {
        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        warehouse.setRejectReason("Lý do cũ");

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseResponse response = warehouseService.verifyWarehouse(warehouseId);

        assertNotNull(response);
        assertEquals(WarehouseStatus.AVAILABLE.name(), response.getStatus());
        assertNull(response.getRejectReason());
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
    void contactRequestRejectsUnverifiedWarehouse() {
        warehouse.setStatus(WarehouseStatus.AVAILABLE);
        warehouse.setVerified(false);
        when(warehouseRepository.findPublicAvailableById(warehouseId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> warehouseService.getOwnerContact(warehouseId));
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

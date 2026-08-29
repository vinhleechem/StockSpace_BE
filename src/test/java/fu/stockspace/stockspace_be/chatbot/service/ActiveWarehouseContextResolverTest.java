package fu.stockspace.stockspace_be.chatbot.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.entity.AssignmentStatus;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ActiveWarehouseContextResolverTest {

    private final WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
    private final TenantWarehouseAccessService accessService = mock(TenantWarehouseAccessService.class);
    private final TenantMemberRepository tenantMemberRepository = mock(TenantMemberRepository.class);
    private final StaffWarehouseAssignmentRepository assignmentRepository =
            mock(StaffWarehouseAssignmentRepository.class);
    private final ActiveWarehouseContextResolver resolver = new ActiveWarehouseContextResolver(
            warehouseRepository, accessService, tenantMemberRepository, assignmentRepository);

    @Test
    void resolvesOwnerContextOnlyAfterOwnershipCheck() {
        UUID ownerId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("Kho Bình Tân").build();
        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));

        ChatRequestContext context = resolver.resolve(ownerId, "ROLE_OWNER", warehouseId);

        assertEquals(warehouseId, context.activeWarehouseId());
        assertEquals("Kho Bình Tân", context.activeWarehouseName());
    }

    @Test
    void doesNotExposeTenantWarehouseNameWithoutAnActiveContract() {
        UUID tenantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(accessService.canObserveWarehouse(tenantId, warehouseId))
                .thenReturn(false);

        ChatRequestContext context = resolver.resolve(tenantId, "ROLE_TENANT", warehouseId);

        assertNull(context.activeWarehouseId());
        assertNull(context.activeWarehouseName());
        verifyNoInteractions(warehouseRepository);
    }

    @Test
    void resolvesStaffContextOnlyForAssignedWarehouseWithActiveContract() {
        UUID staffId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        TenantMember membership = TenantMember.builder()
                .tenant(User.builder().id(tenantId).build())
                .build();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("Kho Thủ Đức").build();
        when(tenantMemberRepository.findByUserIdAndIsActiveTrueAndIsDeletedFalse(staffId))
                .thenReturn(Optional.of(membership));
        when(assignmentRepository.existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE)).thenReturn(true);
        when(accessService.canObserveWarehouse(tenantId, warehouseId))
                .thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        ChatRequestContext context = resolver.resolve(staffId, "ROLE_STAFF", warehouseId);

        assertEquals(warehouseId, context.activeWarehouseId());
        assertEquals("Kho Thủ Đức", context.activeWarehouseName());
        verify(assignmentRepository).existsActiveByStaffAndTenantAndWarehouse(
                staffId, tenantId, warehouseId, AssignmentStatus.ACTIVE);
    }
}

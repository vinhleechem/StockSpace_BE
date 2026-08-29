package fu.stockspace.stockspace_be.chatbot.tool.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.chatbot.tool.ChatRequestContext;
import fu.stockspace.stockspace_be.staff.entity.StaffWarehouseAssignment;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetCurrentWarehouseStaffToolTest {

    @Test
    void readsOnlyCurrentWarehouseOwnedByUserAndDoesNotExposeIds() {
        WarehouseRepository warehouseRepository = mock(WarehouseRepository.class);
        StaffWarehouseAssignmentRepository assignmentRepository =
                mock(StaffWarehouseAssignmentRepository.class);
        GetCurrentWarehouseStaffTool tool = new GetCurrentWarehouseStaffTool(
                new ObjectMapper(), warehouseRepository, assignmentRepository);

        UUID ownerId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("Kho Bình Tân").build();
        User staff = User.builder().id(staffId).fullName("Nguyễn Văn An").build();
        StaffWarehouseAssignment assignment = StaffWarehouseAssignment.builder()
                .staff(staff)
                .warehouse(warehouse)
                .customTitle("Thủ kho")
                .startDate(LocalDateTime.of(2026, 8, 1, 8, 0))
                .build();

        when(warehouseRepository.findByIdAndOwnerId(warehouseId, ownerId))
                .thenReturn(Optional.of(warehouse));
        when(assignmentRepository.findActiveByWarehouseId(warehouseId))
                .thenReturn(List.of(assignment));

        String json = tool.executeWithContext(Map.of(), new ChatRequestContext(
                ownerId, "ROLE_OWNER", warehouseId));

        assertTrue(json.contains("Kho Bình Tân"));
        assertTrue(json.contains("Nguyễn Văn An"));
        assertFalse(json.contains(warehouseId.toString()));
        assertFalse(json.contains(staffId.toString()));
        verify(assignmentRepository).findActiveByWarehouseId(warehouseId);
    }

    @Test
    void asksForScreenSelectionInsteadOfAUuidWhenContextIsMissing() {
        GetCurrentWarehouseStaffTool tool = new GetCurrentWarehouseStaffTool(
                new ObjectMapper(), mock(WarehouseRepository.class),
                mock(StaffWarehouseAssignmentRepository.class));

        String json = tool.executeWithContext(Map.of(), new ChatRequestContext(
                UUID.randomUUID(), "ROLE_OWNER", null));

        assertTrue(json.contains("chọn kho trên giao diện"));
        assertFalse(json.contains("UUID"));
    }
}

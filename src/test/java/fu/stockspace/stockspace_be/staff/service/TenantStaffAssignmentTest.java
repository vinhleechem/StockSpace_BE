package fu.stockspace.stockspace_be.staff.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.contract.repository.RentalContractRepository;
import fu.stockspace.stockspace_be.staff.dto.AssignWarehouseRequest;
import fu.stockspace.stockspace_be.staff.dto.StaffAssignmentResponse;
import fu.stockspace.stockspace_be.staff.dto.StaffWorkHistoryResponse;
import fu.stockspace.stockspace_be.staff.entity.*;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantStaffAssignmentTest {

    @Mock
    private TenantMemberRepository memberRepository;

    @Mock
    private StaffWarehouseAssignmentRepository assignmentRepository;

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private RentalContractRepository contractRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TenantStaffService staffService;

    private UUID tenantId;
    private UUID staffUserId;
    private UUID warehouseId;
    private User tenantUser;
    private User staffUser;
    private Warehouse warehouse;
    private TenantMember member;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();

        tenantUser = User.builder().id(tenantId).fullName("Tenant Corp").email("tenant@corp.com").build();
        staffUser = User.builder().id(staffUserId).fullName("Staff John").email("john@corp.com").build();
        warehouse = Warehouse.builder().id(warehouseId).name("Kho Hà Nội").address("Hà Nội").build();

        member = TenantMember.builder()
                .id(UUID.randomUUID())
                .tenant(tenantUser)
                .user(staffUser)
                .isActive(true)
                .isDeleted(false)
                .joinedAt(LocalDateTime.now().minusMonths(3))
                .build();
    }

    @Test
    void assignWarehouseToStaff_success() {
        AssignWarehouseRequest req = AssignWarehouseRequest.builder()
                .warehouseId(warehouseId)
                .customTitle("Trưởng Kho Hà Nội")
                .notes("Phân công quản lý ca 1")
                .build();

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
        when(memberRepository.existsByUserIdAndTenantIdAndIsDeletedFalse(staffUserId, tenantId)).thenReturn(true);
        when(contractRepository.existsByTenantIdAndWarehouseIdAndStatusActive(tenantId, warehouseId)).thenReturn(true);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(assignmentRepository.findByStaffIdAndTenantIdAndStatus(staffUserId, tenantId, AssignmentStatus.ACTIVE))
                .thenReturn(List.of());

        StaffWarehouseAssignment savedAssignment = StaffWarehouseAssignment.builder()
                .id(UUID.randomUUID())
                .staff(staffUser)
                .tenant(tenantUser)
                .warehouse(warehouse)
                .customTitle("Trưởng Kho Hà Nội")
                .assignedBy(tenantUser)
                .startDate(LocalDateTime.now())
                .status(AssignmentStatus.ACTIVE)
                .notes("Phân công quản lý ca 1")
                .build();

        when(assignmentRepository.save(any(StaffWarehouseAssignment.class))).thenReturn(savedAssignment);

        StaffAssignmentResponse response = staffService.assignWarehouseToStaff(tenantId, staffUserId, req);

        assertNotNull(response);
        assertEquals("Trưởng Kho Hà Nội", response.getCustomTitle());
        assertEquals(warehouseId, response.getWarehouseId());
        assertEquals(AssignmentStatus.ACTIVE, response.getStatus());
    }

    @Test
    void removeStaff_shouldSetResignedAtAndRevokeActiveAssignments() {
        UUID memberId = member.getId();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        StaffWarehouseAssignment activeAssignment = StaffWarehouseAssignment.builder()
                .id(UUID.randomUUID())
                .staff(staffUser)
                .tenant(tenantUser)
                .warehouse(warehouse)
                .status(AssignmentStatus.ACTIVE)
                .startDate(LocalDateTime.now().minusDays(10))
                .build();

        when(assignmentRepository.findByStaffIdAndTenantIdAndStatus(staffUserId, tenantId, AssignmentStatus.ACTIVE))
                .thenReturn(List.of(activeAssignment));

        staffService.removeStaff(tenantId, memberId);

        assertTrue(member.isDeleted());
        assertFalse(member.isActive());
        assertNotNull(member.getResignedAt());
        assertEquals(AssignmentStatus.REVOKED, activeAssignment.getStatus());
        assertNotNull(activeAssignment.getEndDate());

        verify(memberRepository).save(member);
        verify(assignmentRepository).saveAll(anyList());
    }

    @Test
    void getStaffWorkHistory_success() {
        when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
        when(memberRepository.findByUserIdOrderByJoinedAtDesc(staffUserId)).thenReturn(List.of(member));

        StaffWarehouseAssignment pastAssignment = StaffWarehouseAssignment.builder()
                .id(UUID.randomUUID())
                .staff(staffUser)
                .tenant(tenantUser)
                .warehouse(warehouse)
                .startDate(LocalDateTime.now().minusMonths(2))
                .endDate(LocalDateTime.now().minusMonths(1))
                .status(AssignmentStatus.EXPIRED)
                .assignedBy(tenantUser)
                .build();

        when(assignmentRepository.findAllCareerAssignmentsByStaffId(staffUserId)).thenReturn(List.of(pastAssignment));

        StaffWorkHistoryResponse history = staffService.getStaffWorkHistory(staffUserId);

        assertNotNull(history);
        assertEquals(staffUserId, history.getStaffId());
        assertEquals(1, history.getTenantTenures().size());
        assertEquals(1, history.getWarehouseAssignments().size());
    }
}

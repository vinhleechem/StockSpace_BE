package fu.stockspace.stockspace_be.staff.service;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.service.EmailService;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.dto.*;
import fu.stockspace.stockspace_be.staff.entity.InvitationStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffInvitation;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.StaffInvitationRepository;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;



import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantStaffServiceTest {

    @Mock private TenantMemberRepository memberRepository;
    @Mock private StaffInvitationRepository invitationRepository;
    @Mock private StaffWarehouseAssignmentRepository assignmentRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private TenantWarehouseAccessService accessService;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private TenantStaffService staffService;


    private UUID tenantId;
    private User tenantUser;
    private Subscription activeSubscription;
    private ServicePackage servicePackage;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        tenantUser = User.builder()
                .id(tenantId)
                .email("tenant@stockspace.com")
                .fullName("Test Tenant")
                .isActive(true)
                .build();

        servicePackage = ServicePackage.builder()
                .id(UUID.randomUUID())
                .name("Pro Package")
                .maxStaff(5)
                .build();

        activeSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(tenantUser)
                .servicePackage(servicePackage)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(30))
                .status(SubscriptionStatus.ACTIVE)
                .build();
    }



    @Test
    void testSendInvitation_Success() {
        InviteStaffRequest request = new InviteStaffRequest();
        request.setEmail("staff@example.com");
        request.setFullName("Le Staff");
        request.setPhone("0987654321");

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSubscription));
        when(memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId)).thenReturn(2L);
        when(invitationRepository.existsByEmailAndTenantIdAndStatus("staff@example.com", tenantId, InvitationStatus.PENDING))
                .thenReturn(false);
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.empty());

        InvitationSentResponse response = staffService.sendInvitation(tenantId, request);

        assertNotNull(response);
        assertEquals("staff@example.com", response.getEmail());
        assertEquals("Le Staff", response.getFullName());
        verify(invitationRepository, times(1)).save(any(StaffInvitation.class));
        verify(emailService, times(1)).sendStaffInvitationEmail(
                eq("staff@example.com"), eq("Le Staff"), eq("Test Tenant"), anyString());
    }

    @Test
    void testSendInvitation_QuotaExceeded() {
        InviteStaffRequest request = new InviteStaffRequest();
        request.setEmail("staff@example.com");
        request.setFullName("Le Staff");

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSubscription));
        when(memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId)).thenReturn(5L);

        assertThrows(BadRequestException.class, () -> staffService.sendInvitation(tenantId, request));
        verify(invitationRepository, never()).save(any(StaffInvitation.class));
    }

    @Test
    void testSendInvitation_QuotaExceededIncludingPendingInvitations() {
        InviteStaffRequest request = new InviteStaffRequest();
        request.setEmail("staff3@example.com");
        request.setFullName("Le Staff 3");

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSubscription));
        when(memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId)).thenReturn(3L);
        when(invitationRepository.countByTenantIdAndStatus(tenantId, InvitationStatus.PENDING)).thenReturn(2L);

        assertThrows(BadRequestException.class, () -> staffService.sendInvitation(tenantId, request));
        verify(invitationRepository, never()).save(any(StaffInvitation.class));
    }

    @Test
    void testSendInvitation_DuplicateInvitation() {
        InviteStaffRequest request = new InviteStaffRequest();
        request.setEmail("staff@example.com");
        request.setFullName("Le Staff");

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSubscription));
        when(memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId)).thenReturn(2L);
        when(invitationRepository.existsByEmailAndTenantIdAndStatus("staff@example.com", tenantId, InvitationStatus.PENDING))
                .thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> staffService.sendInvitation(tenantId, request));
        verify(invitationRepository, never()).save(any(StaffInvitation.class));
    }

    @Test
    void testSendInvitation_CannotInviteTenantOrOwner() {
        InviteStaffRequest request = new InviteStaffRequest();
        request.setEmail("existingtenant@example.com");
        request.setFullName("Existing Tenant");

        Role tenantRole = Role.builder().name(RoleType.ROLE_TENANT.name()).build();
        User existingTenantUser = User.builder()
                .id(UUID.randomUUID())
                .email("existingtenant@example.com")
                .roles(Set.of(tenantRole))
                .build();

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenantUser));
        when(subscriptionRepository.findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                eq(tenantId), eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(Optional.of(activeSubscription));
        when(memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId)).thenReturn(2L);
        when(invitationRepository.existsByEmailAndTenantIdAndStatus("existingtenant@example.com", tenantId, InvitationStatus.PENDING))
                .thenReturn(false);
        when(userRepository.findByEmail("existingtenant@example.com")).thenReturn(Optional.of(existingTenantUser));

        assertThrows(BadRequestException.class, () -> staffService.sendInvitation(tenantId, request));
        verify(invitationRepository, never()).save(any(StaffInvitation.class));
    }



    @Test
    void testPreviewInvitation_Success() {
        String token = "valid-token";
        StaffInvitation invitation = StaffInvitation.builder()
                .email("staff@example.com")
                .fullName("Le Staff")
                .tenant(tenantUser)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

        InvitationPreviewResponse response = staffService.previewInvitation(token);

        assertTrue(response.isValid());
        assertEquals("staff@example.com", response.getEmail());
        assertEquals("Test Tenant", response.getTenantName());
    }

    @Test
    void testPreviewInvitation_Expired() {
        String token = "expired-token";
        StaffInvitation invitation = StaffInvitation.builder()
                .email("staff@example.com")
                .fullName("Le Staff")
                .tenant(tenantUser)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        when(invitationRepository.findByToken(token)).thenReturn(Optional.of(invitation));

        InvitationPreviewResponse response = staffService.previewInvitation(token);

        assertFalse(response.isValid());
        assertTrue(response.getMessage().contains("hết hạn"));
    }



    @Test
    void testAcceptInvitation_NewUser_Success() {
        AcceptInvitationRequest request = new AcceptInvitationRequest();
        request.setToken("token-xyz");
        request.setPassword("Password123!");
        request.setConfirmPassword("Password123!");

        StaffInvitation invitation = StaffInvitation.builder()
                .email("newstaff@example.com")
                .fullName("New Staff")
                .phone("0987654321")
                .tenant(tenantUser)
                .status(InvitationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        Role staffRole = Role.builder().name(RoleType.ROLE_STAFF.name()).build();

        when(invitationRepository.findByToken("token-xyz")).thenReturn(Optional.of(invitation));
        when(userRepository.findByEmail("newstaff@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName(RoleType.ROLE_STAFF.name())).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode("Password123!")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        staffService.acceptInvitation(request);

        assertEquals(InvitationStatus.ACCEPTED, invitation.getStatus());
        verify(memberRepository, times(1)).save(any(TenantMember.class));
    }



    @Test
    void testListStaffs() {
        Pageable pageable = PageRequest.of(0, 10);
        User staffUser = User.builder().id(UUID.randomUUID()).email("staff@example.com").fullName("Le Staff").build();
        TenantMember member = TenantMember.builder().id(UUID.randomUUID()).user(staffUser).tenant(tenantUser).isActive(true).build();
        Page<TenantMember> page = new PageImpl<>(List.of(member));

        when(memberRepository.searchStaffs(tenantId, "Staff", pageable)).thenReturn(page);

        Page<StaffMemberResponse> response = staffService.listStaffs(tenantId, "Staff", pageable);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("staff@example.com", response.getContent().get(0).getEmail());
    }



    @Test
    void testRemoveStaff_Success() {
        UUID memberId = UUID.randomUUID();
        User staffUser = User.builder().id(UUID.randomUUID()).email("staff@test.com").build();
        TenantMember member = TenantMember.builder()
                .id(memberId)
                .tenant(tenantUser)
                .user(staffUser)
                .isActive(true)
                .isDeleted(false)
                .build();

        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        staffService.removeStaff(tenantId, memberId);

        assertTrue(member.isDeleted());
        assertFalse(member.isActive());
        assertNotNull(member.getResignedAt());
        verify(memberRepository, times(1)).save(member);
    }




    @Test
    void testDeactivateExcessStaffs_Triggered() {
        TenantMember m1 = TenantMember.builder().id(UUID.randomUUID()).isActive(true).isDeleted(false).build();
        TenantMember m2 = TenantMember.builder().id(UUID.randomUUID()).isActive(true).isDeleted(false).build();
        TenantMember m3 = TenantMember.builder().id(UUID.randomUUID()).isActive(true).isDeleted(false).build();


        when(memberRepository.findActiveStaffsOrderByJoinedAtAsc(tenantId)).thenReturn(List.of(m1, m2, m3));

        staffService.deactivateExcessStaffs(tenantId, 2);

        assertTrue(m1.isActive());
        assertTrue(m2.isActive());
        assertFalse(m3.isActive());
        verify(memberRepository, times(1)).save(m3);
    }
}

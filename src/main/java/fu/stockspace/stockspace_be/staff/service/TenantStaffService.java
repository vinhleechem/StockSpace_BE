package fu.stockspace.stockspace_be.staff.service;

import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.auth.service.EmailService;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.service.TenantWarehouseAccessService;
import fu.stockspace.stockspace_be.staff.dto.*;
import fu.stockspace.stockspace_be.staff.entity.*;
import fu.stockspace.stockspace_be.staff.repository.StaffInvitationRepository;
import fu.stockspace.stockspace_be.staff.repository.StaffWarehouseAssignmentRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.UUID;




@Slf4j
@Service
@RequiredArgsConstructor
public class TenantStaffService {

    private final TenantMemberRepository memberRepository;
    private final StaffInvitationRepository invitationRepository;
    private final StaffWarehouseAssignmentRepository assignmentRepository;
    private final WarehouseRepository warehouseRepository;
    private final TenantWarehouseAccessService accessService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;












    @Transactional
    public InvitationSentResponse sendInvitation(UUID tenantId, InviteStaffRequest request) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));


        Subscription activeSubscription = subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now())
                .orElseThrow(() -> new BadRequestException(ErrorCode.SUBSCRIPTION_REQUIRED));

        ServicePackage activePkg = activeSubscription.getServicePackage();
        int maxStaff = (activeSubscription.getSnapshotMaxStaff() != null && activeSubscription.getSnapshotMaxStaff() > 0)
                ? activeSubscription.getSnapshotMaxStaff()
                : (activePkg != null && activePkg.getMaxStaff() != null ? activePkg.getMaxStaff() : 0);
        if (maxStaff > 0) {
            long activeStaffCount = memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId);
            long pendingInviteCount = invitationRepository.countByTenantIdAndStatus(tenantId, InvitationStatus.PENDING);
            if ((activeStaffCount + pendingInviteCount) >= maxStaff) {
                throw new BadRequestException(ErrorCode.STAFF_LIMIT_EXCEEDED);
            }
        }


        String email = request.getEmail().toLowerCase().trim();
        if (invitationRepository.existsByEmailAndTenantIdAndStatus(email, tenantId, InvitationStatus.PENDING)) {
            throw new ResourceConflictException(ErrorCode.STAFF_INVITATION_DUPLICATE);
        }


        userRepository.findByEmail(email).ifPresent(existingUser -> {
            boolean isTenantOrOwner = existingUser.getRoles().stream()
                    .anyMatch(r -> RoleType.ROLE_TENANT.name().equals(r.getName()) || RoleType.ROLE_OWNER.name().equals(r.getName()));
            if (isTenantOrOwner) {
                throw new BadRequestException(ErrorCode.STAFF_CANNOT_INVITE_TENANT_OR_OWNER);
            }
            if (memberRepository.existsByUserIdAndTenantIdAndIsDeletedFalse(existingUser.getId(), tenantId)) {
                throw new ResourceConflictException(ErrorCode.STAFF_ALREADY_MEMBER);
            }
        });


        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(48);

        StaffInvitation invitation = StaffInvitation.builder()
                .email(email)
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .tenant(tenant)
                .token(token)
                .expiresAt(expiresAt)
                .status(InvitationStatus.PENDING)
                .build();
        invitationRepository.save(invitation);


        emailService.sendStaffInvitationEmail(email, request.getFullName(), tenant.getFullName(), token);

        log.info("Staff invitation sent: tenant={} → email={}", tenantId, email);

        return InvitationSentResponse.builder()
                .email(email)
                .fullName(request.getFullName())
                .expiresAt(expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .message("Lời mời đã được gửi đến " + email + ". Lời mời có hiệu lực trong 48 giờ.")
                .build();
    }







    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String token) {
        StaffInvitation invitation = invitationRepository.findByToken(token)
                .orElse(null);

        if (invitation == null) {
            return InvitationPreviewResponse.builder()
                    .valid(false)
                    .message("Lời mời không tồn tại hoặc đã bị hủy.")
                    .build();
        }

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            return InvitationPreviewResponse.builder()
                    .valid(false)
                    .message("Lời mời này đã được sử dụng trước đó.")
                    .build();
        }

        if (invitation.isExpired() || invitation.getStatus() == InvitationStatus.EXPIRED) {

            return InvitationPreviewResponse.builder()
                    .valid(false)
                    .message("Lời mời đã hết hạn. Vui lòng yêu cầu doanh nghiệp gửi lại lời mời mới.")
                    .build();
        }

        User tenant = invitation.getTenant();
        return InvitationPreviewResponse.builder()
                .email(invitation.getEmail())
                .fullName(invitation.getFullName())
                .tenantName(tenant.getFullName())
                .tenantEmail(tenant.getEmail())
                .valid(true)
                .build();
    }













    @Transactional
    public void acceptInvitation(AcceptInvitationRequest request) {

        StaffInvitation invitation = invitationRepository.findByToken(request.getToken())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_INVITATION_NOT_FOUND));

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new BadRequestException(ErrorCode.STAFF_INVITATION_ALREADY_ACCEPTED);
        }

        if (invitation.isExpired()) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            throw new BadRequestException(ErrorCode.STAFF_INVITATION_EXPIRED);
        }


        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }


        User staffUser = userRepository.findByEmail(invitation.getEmail())
                .orElseGet(() -> createNewStaffUser(invitation, request.getPassword()));


        if (!staffUser.isActive()) {
            staffUser.setActive(true);
            userRepository.save(staffUser);
        }


        UUID tenantId = invitation.getTenant().getId();
        if (memberRepository.existsByUserIdAndTenantIdAndIsDeletedFalse(
                staffUser.getId(), tenantId)) {
            throw new ResourceConflictException(ErrorCode.STAFF_ALREADY_MEMBER);
        }


        subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now())
                .ifPresent(sub -> {
                    ServicePackage pkg = sub.getServicePackage();
                    int maxStaff = (sub.getSnapshotMaxStaff() != null && sub.getSnapshotMaxStaff() > 0)
                            ? sub.getSnapshotMaxStaff()
                            : (pkg != null && pkg.getMaxStaff() != null ? pkg.getMaxStaff() : 0);
                    if (maxStaff > 0) {
                        long activeStaffCount = memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId);
                        if (activeStaffCount >= maxStaff) {
                            throw new BadRequestException(ErrorCode.STAFF_LIMIT_EXCEEDED);
                        }
                    }
                });


        TenantMember member = TenantMember.builder()
                .user(staffUser)
                .tenant(invitation.getTenant())
                .isActive(true)
                .isDeleted(false)
                .build();
        memberRepository.save(member);


        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepository.save(invitation);

        log.info("Staff invitation accepted: user={} joined tenant={}",
                staffUser.getId(), invitation.getTenant().getId());
    }

    private User createNewStaffUser(StaffInvitation invitation, String rawPassword) {
        Role staffRole = roleRepository.findByName(RoleType.ROLE_STAFF.name())
                .orElseThrow(() -> new IllegalStateException("ROLE_STAFF không tồn tại trong DB. Kiểm tra DataInitializer."));

        User newUser = User.builder()
                .email(invitation.getEmail())
                .password(passwordEncoder.encode(rawPassword))
                .fullName(invitation.getFullName())
                .phone(invitation.getPhone())
                .roles(Set.of(staffRole))
                .isActive(true)
                .isDeleted(false)
                .build();
        return userRepository.save(newUser);
    }






    @Transactional(readOnly = true)
    public Page<StaffMemberResponse> listStaffs(UUID tenantId, String keyword, Pageable pageable) {
        String kw = (keyword != null) ? keyword.trim() : "";
        return memberRepository.searchStaffs(tenantId, kw, pageable)
                .map(this::mapToResponse);
    }







    @Transactional
    public void removeStaff(UUID tenantId, UUID memberId) {
        TenantMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND));


        if (!member.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND);
        }

        if (member.isDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        member.setDeleted(true);
        member.setActive(false);
        member.setResignedAt(now);
        memberRepository.save(member);


        List<StaffWarehouseAssignment> activeAssignments = assignmentRepository
                .findByStaffIdAndTenantIdAndStatus(member.getUser().getId(), tenantId, AssignmentStatus.ACTIVE);
        for (StaffWarehouseAssignment a : activeAssignments) {
            a.setStatus(AssignmentStatus.REVOKED);
            a.setEndDate(now);
        }
        assignmentRepository.saveAll(activeAssignments);

        log.info("Staff removed: memberId={} from tenantId={}, revoked {} active warehouse assignments",
                memberId, tenantId, activeAssignments.size());
    }



    @Transactional
    public StaffAssignmentResponse assignWarehouseToStaff(UUID tenantId, UUID staffUserId, AssignWarehouseRequest request) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND));


        boolean isMember = memberRepository.existsByUserIdAndTenantIdAndIsActiveTrueAndIsDeletedFalse(staffUserId, tenantId);
        if (!isMember) {
            throw new BadRequestException(ErrorCode.STAFF_NOT_FOUND);
        }


        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND));
        accessService.requireWmsAccess(tenantId, warehouse.getId());


        List<StaffWarehouseAssignment> existing = assignmentRepository
                .findByStaffIdAndTenantIdAndStatus(staffUserId, tenantId, AssignmentStatus.ACTIVE);
        for (StaffWarehouseAssignment a : existing) {
            if (a.getWarehouse().getId().equals(request.getWarehouseId())) {
                a.setCustomTitle(request.getCustomTitle());
                a.setNotes(request.getNotes());
                return mapToAssignmentResponse(assignmentRepository.save(a));
            }
        }


        StaffWarehouseAssignment assignment = StaffWarehouseAssignment.builder()
                .staff(staff)
                .tenant(tenant)
                .warehouse(warehouse)
                .customTitle(request.getCustomTitle())
                .assignedBy(tenant)
                .startDate(LocalDateTime.now())
                .status(AssignmentStatus.ACTIVE)
                .notes(request.getNotes())
                .build();

        return mapToAssignmentResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public void revokeWarehouseAssignment(UUID tenantId, UUID assignmentId) {
        StaffWarehouseAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND));

        if (!assignment.getTenant().getId().equals(tenantId)) {
            throw new fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException(ErrorCode.FORBIDDEN);
        }

        accessService.requireWmsAccess(tenantId, assignment.getWarehouse().getId());

        assignment.setStatus(AssignmentStatus.REVOKED);
        assignment.setEndDate(LocalDateTime.now());
        assignmentRepository.save(assignment);
    }

    @Transactional(readOnly = true)
    public List<StaffAssignmentResponse> getStaffAssignments(UUID tenantId, UUID staffUserId) {
        List<StaffWarehouseAssignment> list = assignmentRepository
                .findByTenantIdAndStaffIdOrderByStartDateDesc(tenantId, staffUserId);
        return list.stream().map(this::mapToAssignmentResponse).toList();
    }



    @Transactional(readOnly = true)
    public StaffWorkHistoryResponse getStaffWorkHistory(UUID staffUserId) {
        User staff = userRepository.findById(staffUserId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        List<TenantMember> memberships = memberRepository.findByUserIdOrderByJoinedAtDesc(staffUserId);

        List<StaffWorkHistoryResponse.TenantTenureResponse> tenures = memberships.stream()
                .map(m -> StaffWorkHistoryResponse.TenantTenureResponse.builder()
                        .membershipId(m.getId())
                        .tenantId(m.getTenant().getId())
                        .tenantName(m.getTenant().getFullName())
                        .tenantEmail(m.getTenant().getEmail())
                        .joinedAt(m.getJoinedAt())
                        .resignedAt(m.getResignedAt())
                        .isActive(m.isActive() && !m.isDeleted())
                        .build())
                .toList();

        List<StaffWarehouseAssignment> assignments = assignmentRepository.findAllCareerAssignmentsByStaffId(staffUserId);
        List<StaffAssignmentResponse> assignmentResponses = assignments.stream()
                .map(this::mapToAssignmentResponse)
                .toList();

        return StaffWorkHistoryResponse.builder()
                .staffId(staff.getId())
                .fullName(staff.getFullName())
                .email(staff.getEmail())
                .phone(staff.getPhone())
                .tenantTenures(tenures)
                .warehouseAssignments(assignmentResponses)
                .build();
    }










    @Transactional
    public void deactivateExcessStaffs(UUID tenantId, int maxStaff) {
        if (maxStaff <= 0) {
            return;
        }

        List<TenantMember> activeStaffs = memberRepository.findActiveStaffsOrderByJoinedAtAsc(tenantId);
        if (activeStaffs.size() <= maxStaff) {
            return;
        }


        List<TenantMember> toDeactivate = activeStaffs.subList(maxStaff, activeStaffs.size());
        for (TenantMember m : toDeactivate) {
            m.setActive(false);
            memberRepository.save(m);
            log.warn("Staff deactivated due to downgrade: memberId={}, tenantId={}", m.getId(), tenantId);
        }
    }



    private StaffMemberResponse mapToResponse(TenantMember member) {
        User user = member.getUser();
        return StaffMemberResponse.builder()
                .memberId(member.getId())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .isActive(member.isActive())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private StaffAssignmentResponse mapToAssignmentResponse(StaffWarehouseAssignment a) {
        return StaffAssignmentResponse.builder()
                .id(a.getId())
                .staffId(a.getStaff().getId())
                .staffName(a.getStaff().getFullName())
                .staffEmail(a.getStaff().getEmail())
                .tenantId(a.getTenant().getId())
                .tenantName(a.getTenant().getFullName())
                .warehouseId(a.getWarehouse().getId())
                .warehouseName(a.getWarehouse().getName())
                .warehouseAddress(a.getWarehouse().getAddress())
                .customTitle(a.getCustomTitle())
                .assignedById(a.getAssignedBy().getId())
                .assignedByName(a.getAssignedBy().getFullName())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .status(a.getStatus())
                .notes(a.getNotes())
                .build();
    }
}


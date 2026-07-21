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
import fu.stockspace.stockspace_be.staff.dto.*;
import fu.stockspace.stockspace_be.staff.entity.InvitationStatus;
import fu.stockspace.stockspace_be.staff.entity.StaffInvitation;
import fu.stockspace.stockspace_be.staff.entity.TenantMember;
import fu.stockspace.stockspace_be.staff.repository.StaffInvitationRepository;
import fu.stockspace.stockspace_be.staff.repository.TenantMemberRepository;
import fu.stockspace.stockspace_be.subscription.entity.Subscription;
import fu.stockspace.stockspace_be.subscription.entity.SubscriptionStatus;
import fu.stockspace.stockspace_be.subscription.repository.SubscriptionRepository;
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

/**
 * Service quản lý toàn bộ vòng đời nhân viên kho (Staff) trong tổ chức Tenant.
 *
 * Luồng chính:
 *  1. Tenant gửi lời mời → sendInvitation()
 *  2. Staff click link, preview token → previewInvitation()
 *  3. Staff thiết lập mật khẩu → acceptInvitation()
 *  4. Tenant quản lý danh sách → listStaffs(), removeStaff()
 *  5. Downgrade gói → deactivateExcessStaffs()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantStaffService {

    private final TenantMemberRepository memberRepository;
    private final StaffInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    // ==================== Gửi Lời Mời ====================

    /**
     * Tenant gửi lời mời nhân viên qua email.
     *
     * Kiểm tra:
     *  1. Quota max_staff chưa đạt giới hạn
     *  2. Chưa tồn tại lời mời PENDING cho email + tenant này
     *  3. Email này chưa là Staff active của Tenant
     */
    @Transactional
    public InvitationSentResponse sendInvitation(UUID tenantId, InviteStaffRequest request) {
        User tenant = userRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        // 1. Kiểm tra quota max_staff
        Subscription activeSubscription = subscriptionRepository
                .findFirstByTenantIdAndStatusAndEndDateGreaterThanEqualOrderByEndDateDesc(
                        tenantId, SubscriptionStatus.ACTIVE, LocalDate.now())
                .orElseThrow(() -> new BadRequestException(ErrorCode.SUBSCRIPTION_REQUIRED));

        int maxStaff = activeSubscription.getServicePackage().getMaxStaff();
        if (maxStaff > 0) { // 0 = không giới hạn
            long currentStaffCount = memberRepository.countByTenantIdAndIsActiveTrueAndIsDeletedFalse(tenantId);
            if (currentStaffCount >= maxStaff) {
                throw new BadRequestException(ErrorCode.STAFF_LIMIT_EXCEEDED);
            }
        }

        // 2. Kiểm tra đã có lời mời PENDING chưa
        String email = request.getEmail().toLowerCase().trim();
        if (invitationRepository.existsByEmailAndTenantIdAndStatus(email, tenantId, InvitationStatus.PENDING)) {
            throw new ResourceConflictException(ErrorCode.STAFF_INVITATION_DUPLICATE);
        }

        // 3. Kiểm tra email đã là Staff active của Tenant chưa
        userRepository.findByEmail(email).ifPresent(existingUser -> {
            if (memberRepository.existsByUserIdAndTenantIdAndIsDeletedFalse(existingUser.getId(), tenantId)) {
                throw new ResourceConflictException(ErrorCode.STAFF_ALREADY_MEMBER);
            }
        });

        // 4. Tạo lời mời với token ngẫu nhiên (hết hạn sau 48 giờ)
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

        // 5. Gửi email bất đồng bộ (không block response)
        emailService.sendStaffInvitationEmail(email, request.getFullName(), tenant.getFullName(), token);

        log.info("Staff invitation sent: tenant={} → email={}", tenantId, email);

        return InvitationSentResponse.builder()
                .email(email)
                .fullName(request.getFullName())
                .expiresAt(expiresAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .message("Lời mời đã được gửi đến " + email + ". Lời mời có hiệu lực trong 48 giờ.")
                .build();
    }

    // ==================== Preview Token (FE dùng trước khi render form) ====================

    /**
     * Validate token và trả về thông tin preview để FE render form nhập mật khẩu.
     * Endpoint public, không cần xác thực.
     */
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
            // Cập nhật trạng thái nếu chưa được cập nhật
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

    // ==================== Xác Nhận Lời Mời (Staff thiết lập mật khẩu) ====================

    /**
     * Staff xác nhận lời mời bằng cách thiết lập mật khẩu.
     *
     * Nếu email đã tồn tại trong hệ thống (là Tenant ở chỗ khác, hoặc Staff cũ):
     *   → Không tạo User mới, chỉ tạo thêm TenantMember liên kết
     *   → Staff vẫn đăng nhập bằng mật khẩu CŨ của họ (không reset)
     *
     * Nếu email chưa tồn tại:
     *   → Tạo User mới với ROLE_STAFF + mật khẩu vừa nhập
     */
    @Transactional
    public void acceptInvitation(AcceptInvitationRequest request) {
        // 1. Validate token
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

        // 2. Validate password match
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }

        // 3. Tìm hoặc tạo User
        User staffUser = userRepository.findByEmail(invitation.getEmail())
                .orElseGet(() -> createNewStaffUser(invitation, request.getPassword()));

        // Nếu User đã tồn tại nhưng đang bị deactivate → kích hoạt lại
        if (!staffUser.isActive()) {
            staffUser.setActive(true);
            userRepository.save(staffUser);
        }

        // 4. Kiểm tra chưa là thành viên của Tenant này
        if (memberRepository.existsByUserIdAndTenantIdAndIsDeletedFalse(
                staffUser.getId(), invitation.getTenant().getId())) {
            throw new ResourceConflictException(ErrorCode.STAFF_ALREADY_MEMBER);
        }

        // 5. Tạo TenantMember mới
        TenantMember member = TenantMember.builder()
                .user(staffUser)
                .tenant(invitation.getTenant())
                .isActive(true)
                .isDeleted(false)
                .build();
        memberRepository.save(member);

        // 6. Cập nhật trạng thái lời mời
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

    // ==================== Danh Sách Nhân Viên ====================

    /**
     * Lấy danh sách nhân viên của Tenant, hỗ trợ tìm kiếm và phân trang.
     */
    @Transactional(readOnly = true)
    public Page<StaffMemberResponse> listStaffs(UUID tenantId, String keyword, Pageable pageable) {
        String kw = (keyword != null) ? keyword.trim() : "";
        return memberRepository.searchStaffs(tenantId, kw, pageable)
                .map(this::mapToResponse);
    }

    // ==================== Xóa Nhân Viên ====================

    /**
     * Tenant xóa mềm nhân viên khỏi tổ chức (is_deleted = true).
     * Lịch sử phiếu nhập/xuất kho vẫn được giữ nguyên.
     */
    @Transactional
    public void removeStaff(UUID tenantId, UUID memberId) {
        TenantMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND));

        // Kiểm tra membership này có thuộc Tenant này không
        if (!member.getTenant().getId().equals(tenantId)) {
            throw new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND);
        }

        if (member.isDeleted()) {
            throw new ResourceNotFoundException(ErrorCode.STAFF_NOT_FOUND);
        }

        member.setDeleted(true);
        member.setActive(false);
        memberRepository.save(member);

        log.info("Staff removed: memberId={} from tenantId={}", memberId, tenantId);
    }

    // ==================== Xử Lý Downgrade Gói ====================

    /**
     * Được gọi sau khi Tenant nâng/hạ cấp gói dịch vụ.
     * Tự động khóa (isActive = false) các nhân viên mới nhất vượt quota mới.
     *
     * @param tenantId  UUID của Tenant
     * @param maxStaff  Giới hạn mới từ gói vừa mua (0 = không giới hạn)
     */
    @Transactional
    public void deactivateExcessStaffs(UUID tenantId, int maxStaff) {
        if (maxStaff <= 0) {
            return; // 0 = không giới hạn, không cần khóa ai
        }

        List<TenantMember> activeStaffs = memberRepository.findActiveStaffsOrderByJoinedAtAsc(tenantId);
        if (activeStaffs.size() <= maxStaff) {
            return; // Không vượt quota, không cần làm gì
        }

        // Giữ maxStaff người đầu tiên (join sớm nhất), khóa phần còn lại
        List<TenantMember> toDeactivate = activeStaffs.subList(maxStaff, activeStaffs.size());
        for (TenantMember m : toDeactivate) {
            m.setActive(false);
            memberRepository.save(m);
            log.warn("Staff deactivated due to downgrade: memberId={}, tenantId={}", m.getId(), tenantId);
        }
    }

    // ==================== Helper ====================

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
}

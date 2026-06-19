package fu.stockspace.stockspace_be.common;
import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.PermissionRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import fu.stockspace.stockspace_be.common.repository.SystemPolicyRepository;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.repository.ServicePackageRepository;
import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;
/**
 * Khởi tạo dữ liệu mẫu (Roles, Permissions, default Users) khi chạy ứng dụng lần đầu.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SystemPolicyRepository systemPolicyRepository;
    private final WalletService walletService;
    private final ServicePackageRepository packageRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final fu.stockspace.stockspace_be.wallet.repository.WalletRepository walletRepository;
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Starting DataInitializer to seed roles and permissions...");
        // 1. Khởi tạo permissions
        Permission whRead = getOrCreatePermission("WAREHOUSE_READ", "Xem thông tin kho bãi");
        Permission whCreate = getOrCreatePermission("WAREHOUSE_CREATE", "Tạo mới kho bãi");
        Permission whUpdate = getOrCreatePermission("WAREHOUSE_UPDATE", "Cập nhật kho bãi");
        Permission whDelete = getOrCreatePermission("WAREHOUSE_DELETE", "Xóa kho bãi");
        Permission rentalCreate = getOrCreatePermission("RENTAL_REQUEST_CREATE", "Tạo yêu cầu thuê kho");
        Permission rentalRead = getOrCreatePermission("RENTAL_REQUEST_READ", "Xem yêu cầu thuê kho");
        Permission rentalProcess = getOrCreatePermission("RENTAL_REQUEST_PROCESS", "Duyệt/từ chối yêu cầu thuê");
        Permission inspectRead = getOrCreatePermission("INSPECTION_READ", "Xem báo cáo thanh tra");
        Permission inspectCreate = getOrCreatePermission("INSPECTION_CREATE", "Tạo báo cáo thanh tra");
        Permission inspectApprove = getOrCreatePermission("INSPECTION_APPROVE", "Phê duyệt thanh tra");
        Permission invRead = getOrCreatePermission("INVENTORY_READ", "Xem tồn kho");
        Permission invCreate = getOrCreatePermission("INVENTORY_CREATE", "Thêm hàng vào kho");
        Permission invUpdate = getOrCreatePermission("INVENTORY_UPDATE", "Cập nhật hàng hóa");
        Permission invDelete = getOrCreatePermission("INVENTORY_DELETE", "Xóa hàng hóa khỏi kho");
        Permission inboundCreate = getOrCreatePermission("INBOUND_CREATE", "Tạo phiếu nhập kho");
        Permission outboundCreate = getOrCreatePermission("OUTBOUND_CREATE", "Tạo phiếu xuất kho");
        Permission staffManage = getOrCreatePermission("STAFF_MANAGE", "Quản lý nhân viên");
        Permission pkgPurchase = getOrCreatePermission("PACKAGE_PURCHASE", "Mua gói dịch vụ");
        // 2. Khởi tạo default roles và gán permissions
        
        // ROLE_ADMIN — có tất cả quyền
        Set<Permission> adminPermissions = new HashSet<>(permissionRepository.findAll());
        getOrCreateRole(RoleType.ROLE_ADMIN.name(), "Administrator với đầy đủ quyền hạn", adminPermissions);
        // ROLE_OWNER — Quản lý kho bãi của họ + duyệt thuê + xem thanh tra
        Set<Permission> ownerPermissions = Set.of(whRead, whCreate, whUpdate, whDelete, rentalRead, rentalProcess, inspectRead);
        getOrCreateRole(RoleType.ROLE_OWNER.name(), "Chủ kho bãi (Warehouse Owner)", ownerPermissions);
        // ROLE_TENANT — Thuê kho, quản lý hàng hóa, staff, mua gói dịch vụ
        Set<Permission> tenantPermissions = Set.of(whRead, rentalCreate, rentalRead, invRead, invCreate, invUpdate, invDelete, inboundCreate, outboundCreate, staffManage, pkgPurchase);
        getOrCreateRole(RoleType.ROLE_TENANT.name(), "Người thuê kho (Tenant)", tenantPermissions);
        // ROLE_STAFF — Quản lý hàng hóa, phiếu nhập/xuất
        Set<Permission> staffPermissions = Set.of(invRead, invCreate, invUpdate, invDelete, inboundCreate, outboundCreate);
        getOrCreateRole(RoleType.ROLE_STAFF.name(), "Nhân viên kho (Warehouse Staff)", staffPermissions);
        // ROLE_INSPECTOR — Thanh tra chất lượng kho
        Set<Permission> inspectorPermissions = Set.of(whRead, inspectRead, inspectCreate, inspectApprove);
        getOrCreateRole(RoleType.ROLE_INSPECTOR.name(), "Thanh tra kho bãi (Inspector)", inspectorPermissions);
        // 3. Khởi tạo default users để tiện test
        createDefaultUser("admin@stockspace.com", "Password123", "System Admin", "0987654321", RoleType.ROLE_ADMIN.name(), BigDecimal.ZERO);
        createDefaultUser("owner@stockspace.com", "Password123", "Nguyen Owner", "0987654322", RoleType.ROLE_OWNER.name(), new BigDecimal("200000000.00"));
        createDefaultUser("tenant@stockspace.com", "Password123", "Tran Tenant", "0987654323", RoleType.ROLE_TENANT.name(), new BigDecimal("100000000.00"));
        createDefaultUser("staff@stockspace.com", "Password123", "Le Staff", "0987654324", RoleType.ROLE_STAFF.name(), BigDecimal.ZERO);
        createDefaultUser("inspector@stockspace.com", "Password123", "Pham Inspector", "0987654325", RoleType.ROLE_INSPECTOR.name(), BigDecimal.ZERO);
        // 4. Khởi tạo chính sách/cam kết ràng buộc mặc định
        seedDefaultSystemPolicy();
        // 5. Khởi tạo các gói dịch vụ mặc định
        seedDefaultPackages();
        // 6. Khởi tạo cấu hình hệ thống mặc định
        seedSystemConfig();
        log.info("DataInitializer finished seeding successfully!");
    }
    private Permission getOrCreatePermission(String name, String description) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> {
                    Permission permission = Permission.builder()
                            .name(name)
                            .description(description)
                            .build();
                    log.info("Seeding permission: {}", name);
                    return permissionRepository.save(permission);
                });
    }
    private void getOrCreateRole(String name, String description, Set<Permission> permissions) {
        Role role = roleRepository.findByName(name)
                .orElseGet(() -> Role.builder()
                        .name(name)
                        .description(description)
                        .build());
        if (role.getPermissions() == null) {
            role.setPermissions(new HashSet<>(permissions));
        } else {
            role.getPermissions().clear();
            role.getPermissions().addAll(permissions);
        }
        roleRepository.save(role);
        log.info("Seeding role: {} with {} permissions", name, permissions.size());
    }
    private void createDefaultUser(String email, String rawPassword, String fullName, String phone, String roleName, BigDecimal initialBalance) {
        if (!userRepository.existsByEmail(email)) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Role not found during user seeding: " + roleName));
            User user = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode(rawPassword))
                    .fullName(fullName)
                    .phone(phone)
                    .roles(Set.of(role))
                    .isActive(true)
                    .build();
            userRepository.save(user);
            user = userRepository.save(user);
            fu.stockspace.stockspace_be.wallet.entity.Wallet wallet = walletService.getOrCreateWallet(user.getId());
            if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
                wallet.setBalance(initialBalance);
                walletRepository.save(wallet);
            }
            log.info("Seeded default user: {} with role {} and balance {}", email, roleName, initialBalance);
        }
    }
    private void seedDefaultSystemPolicy() {
        if (systemPolicyRepository.count() == 0) {
            SystemPolicy policy = SystemPolicy.builder()
                    .version("v1.0")
                    .content("BẢN CAM KẾT RÀNG BUỘC PHÁP LÝ (BẢN CHUẨN HỆ THỐNG)\n" +
                            "1. Đối với Người cho thuê (Owner):\n" +
                            "   - Cam kết cung cấp thông tin kho bãi chính xác, trung thực, bao gồm diện tích, hình ảnh và tình trạng thực tế.\n" +
                            "   - Cam kết chuẩn bị kho sạch sẽ, đúng theo thỏa thuận để bàn giao cho người thuê.\n" +
                            "2. Đối với Người thuê (Tenant):\n" +
                            "   - Cam kết thanh toán đặt cọc 10% đúng hạn để xác nhận thỏa thuận thuê kho.\n" +
                            "   - Cam kết sử dụng kho bãi đúng mục đích thỏa thuận, tuân thủ các quy định phòng cháy chữa cháy và pháp luật.\n" +
                            "3. Điều khoản Tranh chấp (Dispute):\n" +
                            "   - Mọi tranh chấp liên quan đến tiền đặt cọc sẽ được chuyển cho Inspector/Admin phân xử dựa trên bằng chứng do hai bên cung cấp.\n" +
                            "   - Quyết định của Ban quản lý hệ thống StockSpace là quyết định cuối cùng và có tính ràng buộc cao nhất.")
                    .isActive(true)
                    .isDeleted(false)
                    .build();
            systemPolicyRepository.save(policy);
            log.info("Seeded default system policy: {}", policy.getVersion());
        }
    }
    private void seedDefaultPackages() {
        if (packageRepository.count() == 0) {
            packageRepository.save(ServicePackage.builder()
                    .name("Gói Cơ Bản (Basic)")
                    .features("{\"wms\": true, \"max_staff\": 2, \"max_products\": 100}")
                    .price(new BigDecimal("200000.00"))
                    .durationDays(30)
                    .isActive(true)
                    .build());
            packageRepository.save(ServicePackage.builder()
                    .name("Gói Nâng Cao (Advanced)")
                    .features("{\"wms\": true, \"max_staff\": 10, \"max_products\": 1000}")
                    .price(new BigDecimal("500000.00"))
                    .durationDays(30)
                    .isActive(true)
                    .build());
            log.info("Seeded default service packages successfully");
        }
    }
    private void seedSystemConfig() {
        // First check or seed "Phí Đăng Bài Kho Bãi" package
        ServicePackage postingFeePkg = packageRepository.findByName("Phí Đăng Bài Kho Bãi")
                .orElseGet(() -> packageRepository.save(ServicePackage.builder()
                        .name("Phí Đăng Bài Kho Bãi")
                        .features("{\"type\":\"POSTING_FEE\"}")
                        .price(new BigDecimal("50000.00"))
                        .durationDays(0)
                        .isActive(true)
                        .build()));

        if (systemConfigRepository.count() == 0) {
            systemConfigRepository.save(SystemConfig.builder()
                    .configKey("deposit_percentage")
                    .configValue("10")
                    .description("Tỷ lệ phần trăm đặt cọc thuê kho (ví dụ: 10 đại diện cho 10%)")
                    .build());
            systemConfigRepository.save(SystemConfig.builder()
                    .configKey("contract_expiry_days")
                    .configValue("7")
                    .description("Số ngày tối đa để Tenant xác nhận ký hợp đồng online sau khi Owner submit")
                    .build());
            systemConfigRepository.save(SystemConfig.builder()
                    .configKey("warehouse_publish_package_id")
                    .configValue(postingFeePkg.getId().toString())
                    .description("ID của gói dịch vụ Phí Đăng Bài Kho Bãi trong hệ thống")
                    .build());
            log.info("Seeded default system configurations successfully");
        }
    }
}
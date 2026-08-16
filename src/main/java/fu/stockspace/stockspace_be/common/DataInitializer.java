package fu.stockspace.stockspace_be.common;

import fu.stockspace.stockspace_be.auth.entity.Permission;
import fu.stockspace.stockspace_be.auth.entity.Role;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.PermissionRepository;
import fu.stockspace.stockspace_be.auth.repository.RoleRepository;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.chatbot.entity.KnowledgeCategory;
import fu.stockspace.stockspace_be.chatbot.entity.SystemKnowledge;
import fu.stockspace.stockspace_be.common.entity.SystemPolicy;
import fu.stockspace.stockspace_be.common.repository.SystemPolicyRepository;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import fu.stockspace.stockspace_be.subscription.repository.ServicePackageRepository;
import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
        @Value("${app.data.seed-demo-users:false}")
        private boolean seedDemoUsers;

        private final RoleRepository roleRepository;
        private final PermissionRepository permissionRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final SystemPolicyRepository systemPolicyRepository;
        private final WalletService walletService;
        private final ServicePackageRepository packageRepository;
        private final SystemConfigRepository systemConfigRepository;
        private final fu.stockspace.stockspace_be.wallet.repository.WalletRepository walletRepository;
        private final UnitOfMeasureRepository uomRepository;
        private final fu.stockspace.stockspace_be.chatbot.repository.SystemKnowledgeRepository systemKnowledgeRepository;

        @Override
        @Transactional
        public void run(String... args) throws Exception {
                log.info("Starting DataInitializer to seed roles and permissions...");

                Permission whRead = getOrCreatePermission("WAREHOUSE_READ", "Xem thông tin kho bãi");
                Permission whCreate = getOrCreatePermission("WAREHOUSE_CREATE", "Tạo mới kho bãi");
                Permission whUpdate = getOrCreatePermission("WAREHOUSE_UPDATE", "Cập nhật kho bãi");
                Permission whDelete = getOrCreatePermission("WAREHOUSE_DELETE", "Xóa kho bãi");
                Permission rentalCreate = getOrCreatePermission("RENTAL_REQUEST_CREATE", "Tạo yêu cầu thuê kho");
                Permission rentalRead = getOrCreatePermission("RENTAL_REQUEST_READ", "Xem yêu cầu thuê kho");
                Permission rentalProcess = getOrCreatePermission("RENTAL_REQUEST_PROCESS",
                                "Duyệt/từ chối yêu cầu thuê");
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

                Set<Permission> adminPermissions = new HashSet<>(permissionRepository.findAll());
                getOrCreateRole(RoleType.ROLE_ADMIN.name(), "Administrator với đầy đủ quyền hạn", adminPermissions);

                Set<Permission> ownerPermissions = Set.of(whRead, whCreate, whUpdate, whDelete, rentalRead,
                                rentalProcess, inspectRead);
                getOrCreateRole(RoleType.ROLE_OWNER.name(), "Chủ kho bãi (Warehouse Owner)", ownerPermissions);

                Set<Permission> tenantPermissions = Set.of(whRead, rentalCreate, rentalRead, invRead, invCreate,
                                invUpdate, invDelete, inboundCreate, outboundCreate, staffManage, pkgPurchase);
                getOrCreateRole(RoleType.ROLE_TENANT.name(), "Người thuê kho (Tenant)", tenantPermissions);

                Set<Permission> staffPermissions = Set.of(invRead, invCreate, invUpdate, invDelete, inboundCreate,
                                outboundCreate);
                getOrCreateRole(RoleType.ROLE_STAFF.name(), "Nhân viên kho (Warehouse Staff)", staffPermissions);

                Set<Permission> inspectorPermissions = Set.of(whRead, inspectRead, inspectCreate, inspectApprove);
                getOrCreateRole(RoleType.ROLE_INSPECTOR.name(), "Thanh tra kho bãi (Inspector)", inspectorPermissions);

                if (seedDemoUsers) {
                        createDefaultUser("admin@stockspace.com", "Password123", "System Admin", "0987654321",
                                        RoleType.ROLE_ADMIN.name(), BigDecimal.ZERO);
                        createDefaultUser("owner@stockspace.com", "Password123", "Nguyen Owner", "0987654322",
                                        RoleType.ROLE_OWNER.name(), new BigDecimal("200000000.00"));
                        createDefaultUser("tenant@stockspace.com", "Password123", "Tran Tenant", "0987654323",
                                        RoleType.ROLE_TENANT.name(), new BigDecimal("100000000.00"));
                        createDefaultUser("staff@stockspace.com", "Password123", "Le Staff", "0987654324",
                                        RoleType.ROLE_STAFF.name(), BigDecimal.ZERO);
                        createDefaultUser("inspector@stockspace.com", "Password123", "Pham Inspector", "0987654325",
                                        RoleType.ROLE_INSPECTOR.name(), BigDecimal.ZERO);
                } else {
                        log.info("Demo account seeding is disabled");
                }

                seedDefaultSystemPolicy();

                seedDefaultPackages();

                seedSystemConfig();

                seedDefaultUoms();

                seedSystemKnowledge();
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
                if (roleRepository.findByName(name).isPresent()) {
                        return;
                }

                Role role = Role.builder()
                                .name(name)
                                .description(description)
                                .permissions(new HashSet<>(permissions))
                                .build();
                roleRepository.save(role);
                log.info("Seeded new role: {} with {} permissions", name, permissions.size());
        }

        private void createDefaultUser(String email, String rawPassword, String fullName, String phone, String roleName,
                        BigDecimal initialBalance) {
                if (!userRepository.existsByEmail(email)) {
                        Role role = roleRepository.findByName(roleName)
                                        .orElseThrow(() -> new IllegalStateException(
                                                        "Role not found during user seeding: " + roleName));
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
                        fu.stockspace.stockspace_be.wallet.entity.Wallet wallet = walletService
                                        .getOrCreateWallet(user.getId());
                        if (initialBalance.compareTo(BigDecimal.ZERO) > 0) {
                                wallet.setBalance(initialBalance);
                                walletRepository.save(wallet);
                        }
                        log.info("Seeded default user: {} with role {} and balance {}", email, roleName,
                                        initialBalance);
                }
        }

        private void seedDefaultSystemPolicy() {
                if (systemPolicyRepository.count() == 0) {
                        SystemPolicy policy = SystemPolicy.builder()
                                        .version("v1.0")
                                        .content("BẢN CAM KẾT RÀNG BUỘC PHÁP LÝ (BẢN CHUẨN HỆ THỐNG)\n" +
                                                        "1. Đối với Người cho thuê (Owner):\n" +
                                                        "   - Cam kết cung cấp thông tin kho bãi chính xác, trung thực, bao gồm diện tích, hình ảnh và tình trạng thực tế.\n"
                                                        +
                                                        "   - Cam kết chuẩn bị kho sạch sẽ, đúng theo thỏa thuận để bàn giao cho người thuê.\n"
                                                        +
                                                        "2. Đối với Người thuê (Tenant):\n" +
                                                        "   - Cam kết thanh toán đặt cọc 10% đúng hạn để xác nhận thỏa thuận thuê kho.\n"
                                                        +
                                                        "   - Cam kết sử dụng kho bãi đúng mục đích thỏa thuận, tuân thủ các quy định phòng cháy chữa cháy và pháp luật.\n"
                                                        +
                                                        "3. Điều khoản Tranh chấp (Dispute):\n" +
                                                        "   - Mọi tranh chấp liên quan đến tiền đặt cọc sẽ được chuyển cho Inspector/Admin phân xử dựa trên bằng chứng do hai bên cung cấp.\n"
                                                        +
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
                                        .features("{\"wms\": true}")
                                        .price(new BigDecimal("200000.00"))
                                        .durationDays(30)
                                        .maxStaff(2)
                                        .isActive(true)
                                        .build());
                        packageRepository.save(ServicePackage.builder()
                                        .name("Gói Nâng Cao (Advanced)")
                                        .features("{\"wms\": true}")
                                        .price(new BigDecimal("500000.00"))
                                        .durationDays(30)
                                        .maxStaff(10)
                                        .isActive(true)
                                        .build());
                        log.info("Seeded default service packages successfully");
                }
        }

        private void seedSystemConfig() {

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

                if (systemConfigRepository.findByConfigKey("inspection_fee").isEmpty()) {
                        systemConfigRepository.save(SystemConfig.builder()
                                        .configKey("inspection_fee")
                                        .configValue("40000")
                                        .description("Phí gửi yêu cầu kiểm định kho bãi")
                                        .build());
                        log.info("Seeded inspection_fee system configuration successfully");
                }
        }

        private void seedDefaultUoms() {
                if (uomRepository.count() == 0) {
                        log.info("Seeding default UOMs...");
                        uomRepository.save(UnitOfMeasure.builder().code("CAI").name("Cái/Chiếc")
                                        .description("Đơn vị đếm lẻ").build());
                        uomRepository.save(UnitOfMeasure.builder().code("THUNG").name("Thùng")
                                        .description("Đơn vị đóng thùng").build());
                        uomRepository.save(UnitOfMeasure.builder().code("HOP").name("Hộp")
                                        .description("Đơn vị đóng hộp").build());
                        uomRepository.save(
                                        UnitOfMeasure.builder().code("KG").name("Kg").description("Kilogram").build());
                        uomRepository.save(UnitOfMeasure.builder().code("BAO").name("Bao")
                                        .description("Đơn vị đóng bao").build());
                        uomRepository.save(UnitOfMeasure.builder().code("KHOI").name("Khối")
                                        .description("Mét khối (m3)").build());
                }
        }

        private void seedSystemKnowledge() {
                List<KnowledgeSeed> seeds = List.of(
                                new KnowledgeSeed(
                                                "kb.deposit.current",
                                                KnowledgeCategory.POLICY,
                                                "Quy định đặt cọc thuê kho",
                                                "Khoản đặt cọc mặc định bằng 10% giá thuê theo tháng tại thời điểm tạo yêu cầu đặt kho. "
                                                                +
                                                                "Tỷ lệ thực tế có thể được StockSpace điều chỉnh; số tiền hiển thị trên yêu cầu "
                                                                +
                                                                "là căn cứ áp dụng cho giao dịch cụ thể. " +
                                                                "Hệ thống trừ khoản cọc khỏi ví người thuê khi gửi yêu cầu. Nếu yêu cầu còn đang chờ xử lý "
                                                                +
                                                                "và người thuê hủy, hoặc chủ kho từ chối, hệ thống hoàn lại toàn bộ khoản cọc."),
                                new KnowledgeSeed(
                                                "kb.cancellation.current",
                                                KnowledgeCategory.CANCELLATION,
                                                "Chính sách Hủy hợp đồng thuê kho",
                                                "Người thuê chỉ có thể tự hủy yêu cầu đặt kho khi yêu cầu còn đang chờ xử lý và khi đó được hoàn lại khoản cọc. "
                                                                +
                                                                "Trong giai đoạn thương lượng hợp đồng, chủ kho có thể gửi đề nghị hủy để người thuê phản hồi. "
                                                                +
                                                                "Nếu người thuê đồng ý, hợp đồng bị hủy và cọc được hoàn; nếu không đồng ý, vụ việc chuyển "
                                                                +
                                                                "sang tranh chấp. Khoản cọc trong tranh chấp chỉ được xử lý theo kết quả phân xử dựa trên bằng chứng."),
                                new KnowledgeSeed(
                                                "kb.damage-dispute.current",
                                                KnowledgeCategory.INSURANCE,
                                                "Bảo hiểm & Đền bù hàng hóa hư hỏng",
                                                "StockSpace không tự động cấp hợp đồng bảo hiểm hoặc cam kết một mức bồi thường cố định. "
                                                                +
                                                                "Khi có hư hỏng hoặc thất thoát, các bên cần lưu bằng chứng và dùng quy trình tranh chấp; "
                                                                +
                                                                "Bộ phận kiểm định hoặc quản trị viên xử lý khoản cọc theo hồ sơ được cung cấp. Điều kiện bảo hiểm riêng, "
                                                                +
                                                                "nếu có, phải được kiểm tra trong hợp đồng và chứng từ của kho."),
                                new KnowledgeSeed(
                                                "kb.rental-process.current",
                                                KnowledgeCategory.RENTAL_PROCESS,
                                                "Quy trình Thuê kho bãi trên StockSpace",
                                                "Bước 1: Người thuê chọn kho đang cho thuê và gửi yêu cầu đặt kho; khoản cọc được trừ theo số tiền hiển thị. "
                                                                +
                                                                "Bước 2: Chủ kho chấp nhận hoặc từ chối yêu cầu; nếu chấp nhận, hệ thống tạo hợp đồng để hai bên "
                                                                +
                                                                "thương lượng. Bước 3: Chủ kho cập nhật thông tin hợp đồng và chứng từ, sau đó người thuê xác nhận "
                                                                +
                                                                "trong thời hạn StockSpace thông báo. Bước 4: Hợp đồng có hiệu lực và hai bên thực hiện bàn giao."),
                                new KnowledgeSeed(
                                                "kb.wallet-vnpay.current",
                                                KnowledgeCategory.FAQ,
                                                "Làm thế nào để nạp tiền vào ví StockSpace?",
                                                "Luồng nạp tiền hiện tại chuyển người dùng đến cổng VNPAY. Các kênh thanh toán khả dụng do VNPAY "
                                                                +
                                                                "hiển thị tại thời điểm giao dịch. Số dư chỉ được ghi nhận sau khi hệ thống nhận và xác minh "
                                                                +
                                                                "kết quả thanh toán thành công; hãy kiểm tra lịch sử giao dịch nếu số dư chưa cập nhật."));

                int changed = 0;
                for (KnowledgeSeed seed : seeds) {
                        if (upsertKnowledgeSeed(seed)) {
                                changed++;
                        }
                }
                if (changed > 0) {
                        log.info("Seeded or updated {} system knowledge documents without remote embedding calls",
                                        changed);
                }
        }

        private boolean upsertKnowledgeSeed(KnowledgeSeed seed) {
                SystemKnowledge document = systemKnowledgeRepository.findBySourceId(seed.sourceId())
                                .or(() -> systemKnowledgeRepository
                                                .findFirstByTitleIgnoreCaseAndIsDeletedFalse(seed.title()))
                                .orElseGet(() -> SystemKnowledge.builder()
                                                .sourceId(seed.sourceId())
                                                .category(seed.category())
                                                .title(seed.title())
                                                .content(seed.content())
                                                .build());

                boolean isNew = document.getId() == null;
                boolean contentChanged = !Objects.equals(document.getTitle(), seed.title())
                                || !Objects.equals(document.getContent(), seed.content())
                                || document.getCategory() != seed.category();
                boolean metadataChanged = !Objects.equals(document.getSourceId(), seed.sourceId())
                                || !document.isActive()
                                || document.isDeleted()
                                || "[]".equals(document.getEmbeddingStr());

                document.setSourceId(seed.sourceId());
                document.setCategory(seed.category());
                document.setTitle(seed.title());
                document.setContent(seed.content());
                document.setActive(true);
                document.setDeleted(false);

                if (contentChanged || "[]".equals(document.getEmbeddingStr())) {
                        document.clearEmbedding();
                }

                if (isNew || contentChanged || metadataChanged) {
                        systemKnowledgeRepository.save(document);
                        return true;
                }
                return false;
        }

        private record KnowledgeSeed(
                        String sourceId,
                        KnowledgeCategory category,
                        String title,
                        String content) {
        }
}

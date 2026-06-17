package fu.stockspace.stockspace_be.wallet.service;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.exception.exceptions.UnauthorizedException;
import fu.stockspace.stockspace_be.wallet.dto.*;
import fu.stockspace.stockspace_be.wallet.entity.*;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    @Value("${app.sepay.webhook-token:MySecretSePayWebhookToken123}")
    private String sepayWebhookToken;
    @Value("${app.sepay.bank-id:MB}")
    private String sepayBankId;
    @Value("${app.sepay.bank-account-no:123456789}")
    private String sepayBankAccountNo;
    @Value("${app.sepay.account-holder:CONG TY STOCKSPACE}")
    private String sepayAccountHolder;
    /**
     * Lấy ví của người dùng, tự động tạo nếu chưa tồn tại.
     */
    @Transactional
    public Wallet getOrCreateWallet(UUID userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
                    Wallet newWallet = Wallet.builder()
                            .user(user)
                            .balance(BigDecimal.ZERO)
                            .isActive(true)
                            .build();
                    log.info("Lazy-creating wallet for user: {}", userId);
                    return walletRepository.save(newWallet);
                });
    }
    /**
     * Lấy thông tin ví của người dùng.
     */
    @Transactional(readOnly = true)
    public WalletResponse getWalletInfo(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> {
                    // Cần tạo transaction mới để lưu ví nên gọi qua method có Transaction
                    return getOrCreateWallet(userId);
                });
        return mapToWalletResponse(wallet);
    }
    /**
     * Tạo yêu cầu nạp tiền, lưu transaction PENDING và trả về thông tin thanh toán VietQR.
     */
    @Transactional
    public TopUpResponse createTopUpRequest(UUID userId, TopUpRequest request) {
        Wallet wallet = getOrCreateWallet(userId);
        String paymentCode = generatePaymentCode();
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .transactionType(TransactionType.TOP_UP)
                .paymentMethod(request.getPaymentMethod())
                .status(TransactionStatus.PENDING)
                .paymentCode(paymentCode)
                .build();
        transaction = transactionRepository.save(transaction);
        // Sinh link VietQR (chuyển đổi ký tự đặc biệt của tên chủ tài khoản nếu cần)
        String encodedHolder = URLEncoder.encode(sepayAccountHolder, StandardCharsets.UTF_8);
        String qrCodeUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-compact.png?amount=%s&addInfo=%s&accountName=%s",
                sepayBankId,
                sepayBankAccountNo,
                request.getAmount().toPlainString(),
                paymentCode,
                encodedHolder
        );
        return TopUpResponse.builder()
                .transactionId(transaction.getId())
                .paymentCode(paymentCode)
                .amount(request.getAmount())
                .bankName(sepayBankId)
                .bankAccountNumber(sepayBankAccountNo)
                .bankAccountHolder(sepayAccountHolder)
                .qrCodeUrl(qrCodeUrl)
                .build();
    }
    /**
     * Xử lý webhook từ SePay gửi về để cộng tiền ví.
     */
    @Transactional
    public void processSePayWebhook(String authHeader, SePayWebhookRequest payload) {
        // 1. Xác thực Webhook Token
        String expectedAuth = "Apikey " + sepayWebhookToken;
        if (authHeader == null || !authHeader.equals(expectedAuth)) {
            log.error("SePay Webhook unauthorized. Received: {}, Expected: Apikey <secret>", authHeader);
            throw new UnauthorizedException(ErrorCode.UNAUTHENTICATED);
        }
        // 2. Chống trùng lặp (Deduplication)
        String referenceId = payload.getId().toString();
        if (transactionRepository.existsByReferenceId(referenceId)) {
            log.info("SePay Webhook: Transaction referenceId {} already processed. Skipping.", referenceId);
            return;
        }
        // 3. Phân tích nội dung chuyển khoản để tìm mã STSPxxxxxx
        String paymentCode = extractPaymentCode(payload.getCode(), payload.getContent());
        if (paymentCode == null) {
            log.warn("SePay Webhook: Cannot parse payment code from SePay payload (code: '{}', content: '{}'). Skipping.", 
                    payload.getCode(), payload.getContent());
            return;
        }
        // 4. Tìm kiếm transaction PENDING trong hệ thống
        Transaction transaction = transactionRepository.findByPaymentCode(paymentCode)
                .orElse(null);
        if (transaction == null) {
            log.warn("SePay Webhook: Payment code {} matched pattern but no transaction found in DB. Skipping.", paymentCode);
            return;
        }
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.warn("SePay Webhook: Transaction {} is already in status {}. Skipping.", paymentCode, transaction.getStatus());
            return;
        }
        // 5. Khóa ví và cộng tiền vào tài khoản
        UUID userId = transaction.getWallet().getUser().getId();
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        BigDecimal transferAmount = payload.getTransferAmount();
        wallet.setBalance(wallet.getBalance().add(transferAmount));
        walletRepository.save(wallet);
        // 6. Cập nhật transaction thành SUCCESS
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setAmount(transferAmount); // ghi nhận số tiền thực tế nhận được
        transaction.setReferenceId(referenceId);
        transactionRepository.save(transaction);
        log.info("SePay Webhook: Successfully processed transaction {} (referenceId: {}). Credited {} VND to user {}.",
                paymentCode, referenceId, transferAmount, userId);
    }
    /**
     * Khấu trừ số dư ví người dùng (dùng cho thanh toán cọc hoặc gói dịch vụ).
     * Yêu cầu phương thức gọi phải có @Transactional và nên gọi trong service có lock.
     */
    @Transactional
    public Transaction deductBalance(UUID userId, BigDecimal amount, TransactionType type, String description, UUID bookingId, UUID subscriptionId) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletRepository.save(wallet);
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .transactionType(type)
                .paymentMethod(PaymentMethod.WALLET)
                .status(TransactionStatus.SUCCESS)
                .bookingId(bookingId)
                .subscriptionId(subscriptionId)
                .referenceId("SYS-DEDUCT-" + UUID.randomUUID())
                .build();
        log.info("Wallet Service: Deducted {} VND from user {} for {}.", amount, userId, description);
        return transactionRepository.save(transaction);
    }
    /**
     * Hoàn tiền hoặc cộng số dư vào ví người dùng (dùng cho hoàn cọc, phân xử tranh chấp).
     * Yêu cầu phương thức gọi phải có @Transactional và nên gọi trong service có lock.
     */
    @Transactional
    public Transaction refundBalance(UUID userId, BigDecimal amount, TransactionType type, String description, UUID bookingId, UUID subscriptionId) {
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(amount)
                .transactionType(type)
                .paymentMethod(PaymentMethod.WALLET)
                .status(TransactionStatus.SUCCESS)
                .bookingId(bookingId)
                .subscriptionId(subscriptionId)
                .referenceId("SYS-REFUND-" + UUID.randomUUID())
                .build();
        log.info("Wallet Service: Refunded {} VND to user {} for {}.", amount, userId, description);
        return transactionRepository.save(transaction);
    }
    // ==================== Private Helpers ====================
    private String generatePaymentCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random rnd = new Random();
        StringBuilder sb;
        do {
            sb = new StringBuilder("STSP");
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(rnd.nextInt(chars.length())));
            }
        } while (transactionRepository.findByPaymentCode(sb.toString()).isPresent());
        return sb.toString();
    }
    private String extractPaymentCode(String code, String content) {
        Pattern pattern = Pattern.compile("STSP[A-Z0-9]{6}", Pattern.CASE_INSENSITIVE);
        if (code != null) {
            Matcher matcher = pattern.matcher(code);
            if (matcher.find()) {
                return matcher.group().toUpperCase();
            }
        }
        if (content != null) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return matcher.group().toUpperCase();
            }
        }
        return null;
    }
    private WalletResponse mapToWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}

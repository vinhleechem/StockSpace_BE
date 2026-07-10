package fu.stockspace.stockspace_be.wallet.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.wallet.dto.*;
import fu.stockspace.stockspace_be.wallet.entity.*;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final VnPayService vnPayService;

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
                .orElseGet(() -> getOrCreateWallet(userId));
        return mapToWalletResponse(wallet);
    }

    /**
     * Tạo yêu cầu nạp tiền, lưu transaction PENDING và trả về thông tin thanh toán VNPAY.
     */
    @Transactional
    public TopUpResponse createTopUpRequest(UUID userId, TopUpRequest request, String ipAddress) {
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

        // Sinh link thanh toán VNPAY
        String paymentUrl = vnPayService.createPaymentUrl(paymentCode, request.getAmount(), ipAddress);

        return TopUpResponse.builder()
                .transactionId(transaction.getId())
                .paymentUrl(paymentUrl)
                .amount(request.getAmount())
                .build();
    }

    /**
     * Xử lý callback/IPN từ VNPAY gửi về để cập nhật số dư ví.
     */
    @Transactional
    public void processVnPayPayment(Map<String, String> params) {
        log.info("Processing VNPAY payment callback: {}", params);

        // 1. Xác thực chữ ký bảo mật từ VNPAY
        boolean isSignatureValid = vnPayService.verifySignature(params);
        if (!isSignatureValid) {
            throw new BadRequestException("Chữ ký bảo mật VNPAY không hợp lệ");
        }

        String paymentCode = params.get("vnp_TxnRef");
        String vnpResponseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");

        // 2. Tìm giao dịch trong DB
        Transaction transaction = transactionRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYSTEM_ERROR, "Không tìm thấy mã giao dịch: " + paymentCode));

        // 3. Nếu giao dịch đã xử lý xong rồi thì bỏ qua (tránh trùng lặp giữa IPN và Callback)
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            log.info("Transaction {} already processed. Status: {}", paymentCode, transaction.getStatus());
            return;
        }

        // 4. Kiểm tra trạng thái thanh toán từ VNPAY
        if ("00".equals(vnpResponseCode)) {
            // Thanh toán thành công -> Cộng tiền ví
            UUID userId = transaction.getWallet().getUser().getId();
            Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));

            // Số tiền VNPAY gửi về nhân với 100, cần chia lại cho 100
            BigDecimal rawAmount = new BigDecimal(params.get("vnp_Amount"));
            BigDecimal actualAmount = rawAmount.divide(new BigDecimal(100));

            wallet.setBalance(wallet.getBalance().add(actualAmount));
            walletRepository.save(wallet);

            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setAmount(actualAmount);
            transaction.setReferenceId(transactionNo);
            transactionRepository.save(transaction);

            log.info("Successfully credited {} VND to user {} (txnRef: {})", actualAmount, userId, paymentCode);

            // Gửi thông báo
            notificationService.push(
                    userId,
                    "Nạp tiền thành công",
                    "Ví của bạn đã được nạp " + actualAmount + " VND thành công qua cổng thanh toán VNPAY.",
                    "PAYMENT"
            );
        } else {
            // Thanh toán thất bại
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setReferenceId(transactionNo);
            transactionRepository.save(transaction);
            log.warn("VNPAY transaction failed with response code: {} (txnRef: {})", vnpResponseCode, paymentCode);
        }
    }

    /**
     * Khấu trừ số dư ví người dùng (dùng cho thanh toán cọc hoặc gói dịch vụ).
     * Yêu cầu phương thức gọi phải có @Transactional và nên gọi trong service có lock.
     */
    @Transactional
    public Transaction deductBalance(UUID userId, BigDecimal amount, TransactionType type, String description, UUID bookingId, UUID subscriptionId) {
        getOrCreateWallet(userId);
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
        getOrCreateWallet(userId);
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        wallet.setBalance(wallet.getBalance().add(amount));
        log.info("Wallet Service: Updating balance for user {} by adding {}. New balance: {}", userId, amount, wallet.getBalance());
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

    private WalletResponse mapToWalletResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUser().getId())
                .balance(wallet.getBalance())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}

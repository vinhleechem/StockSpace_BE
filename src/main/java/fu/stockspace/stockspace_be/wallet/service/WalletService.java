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
import org.springframework.beans.factory.annotation.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
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

    private final Clock businessClock;

    @Value("${app.wallet.top-up.expiry-minutes:15}")
    private long topUpExpiryMinutes;

    @Value("${app.wallet.top-up.expiry-grace-minutes:0}")
    private long topUpExpiryGraceMinutes;




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
                    return walletRepository.saveAndFlush(newWallet);
                });
    }




    @Transactional(readOnly = true)
    public WalletResponse getWalletInfo(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseGet(() -> getOrCreateWallet(userId));
        return mapToWalletResponse(wallet);
    }




    @Transactional
    public TopUpResponse createTopUpRequest(UUID userId, TopUpRequest request, String ipAddress) {
        Wallet wallet = getOrCreateWallet(userId);
        String paymentCode = generatePaymentCode();
        LocalDateTime expiresAt = LocalDateTime.now(businessClock)
                .plusMinutes(Math.max(topUpExpiryMinutes, 1));

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(request.getAmount())
                .transactionType(TransactionType.TOP_UP)
                .paymentMethod(request.getPaymentMethod())
                .status(TransactionStatus.PENDING)
                .paymentCode(paymentCode)
                .expiresAt(expiresAt)
                .build();
        transaction = transactionRepository.save(transaction);


        String paymentUrl = vnPayService.createPaymentUrl(paymentCode, request.getAmount(), ipAddress);

        return TopUpResponse.builder()
                .transactionId(transaction.getId())
                .paymentUrl(paymentUrl)
                .amount(request.getAmount())
                .expiresAt(expiresAt)
                .build();
    }




    @Transactional
    public void processVnPayPayment(Map<String, String> params) {
        log.info("Processing VNPAY payment callback: {}", params);


        boolean isSignatureValid = vnPayService.verifySignature(params);
        if (!isSignatureValid) {
            throw new BadRequestException("Chữ ký bảo mật VNPAY không hợp lệ");
        }

        String paymentCode = params.get("vnp_TxnRef");
        String vnpResponseCode = params.get("vnp_ResponseCode");
        String transactionNo = params.get("vnp_TransactionNo");


        // Lock the transaction row before checking its state. Wallet locking
        // alone does not prevent concurrent callbacks from both observing
        // PENDING and crediting the same payment twice.
        Transaction transaction = transactionRepository.findByPaymentCodeForUpdate(paymentCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYSTEM_ERROR, "Không tìm thấy mã giao dịch: " + paymentCode));


        if (transaction.getStatus() == TransactionStatus.SUCCESS
                || transaction.getStatus() == TransactionStatus.FAILED) {
            log.info("Transaction {} already processed. Status: {}", paymentCode, transaction.getStatus());
            return;
        }

        if (transaction.getStatus() != TransactionStatus.PENDING
                && transaction.getStatus() != TransactionStatus.EXPIRED) {
            log.info("Transaction {} is not payable. Status: {}", paymentCode, transaction.getStatus());
            return;
        }

        BigDecimal actualAmount = parseAndValidateVnPayAmount(transaction, params);


        TransactionStatus previousStatus = transaction.getStatus();
        if ("00".equals(vnpResponseCode)) {

            UUID userId = transaction.getWallet().getUser().getId();
            Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));


            wallet.setBalance(wallet.getBalance().add(actualAmount));
            walletRepository.save(wallet);

            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setAmount(actualAmount);
            transaction.setReferenceId(transactionNo);
            transactionRepository.save(transaction);

            log.info("Successfully credited {} VND to user {} (txnRef: {}, previousStatus: {})",
                    actualAmount, userId, paymentCode, previousStatus);


            notificationService.push(
                    userId,
                    "Nạp tiền thành công",
                    "Ví của bạn đã được nạp " + actualAmount + " VND thành công qua cổng thanh toán VNPAY.",
                    "PAYMENT"
            );
        } else {

            if (previousStatus == TransactionStatus.PENDING) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setReferenceId(transactionNo);
                transactionRepository.save(transaction);
                log.warn("VNPAY transaction failed with response code: {} (txnRef: {})",
                        vnpResponseCode, paymentCode);
            } else {
                log.info("Keeping expired VNPAY transaction {} as EXPIRED after late failure callback",
                        paymentCode);
            }
        }
    }

    /**
     * Marks abandoned top-up attempts as expired after the VNPAY deadline and
     * an optional grace period. The transaction-row lock serializes this with a
     * late callback, so a valid successful callback can still move EXPIRED to
     * SUCCESS and credit the wallet exactly once.
     */
    @Transactional
    public int expirePendingTopUps() {
        LocalDateTime now = LocalDateTime.now(businessClock);
        long graceMinutes = Math.max(topUpExpiryGraceMinutes, 0);
        LocalDateTime cutoff = now.minusMinutes(graceMinutes);
        LocalDateTime legacyCreatedCutoff = now.minusMinutes(
                Math.max(topUpExpiryMinutes, 1) + graceMinutes);
        List<Transaction> expiredTransactions = transactionRepository
                .findExpiredPendingTopUpsForUpdate(cutoff, legacyCreatedCutoff);

        for (Transaction transaction : expiredTransactions) {
            transaction.setStatus(TransactionStatus.EXPIRED);
            transactionRepository.save(transaction);
            notifyTopUpExpiredAfterCommit(transaction);
        }
        return expiredTransactions.size();
    }

    private BigDecimal parseAndValidateVnPayAmount(Transaction transaction,
                                                   Map<String, String> params) {
        String rawAmount = params.get("vnp_Amount");
        if (rawAmount == null || rawAmount.isBlank()) {
            throw new BadRequestException("VNPAY amount is missing");
        }

        BigDecimal actualAmount;
        try {
            actualAmount = new BigDecimal(rawAmount).divide(new BigDecimal(100));
        } catch (NumberFormatException exception) {
            throw new BadRequestException("VNPAY amount is invalid");
        }

        if (transaction.getAmount() == null
                || transaction.getAmount().compareTo(actualAmount) != 0) {
            throw new BadRequestException("VNPAY amount does not match the pending transaction");
        }
        return actualAmount;
    }

    private void notifyTopUpExpiredAfterCommit(Transaction transaction) {
        try {
            UUID userId = transaction.getWallet().getUser().getId();
            String paymentCode = transaction.getPaymentCode();
            Runnable notifier = () -> notifyTopUpExpired(userId, paymentCode);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        notifier.run();
                    }
                });
            } else {
                // Direct unit calls do not have a Spring transaction boundary.
                notifier.run();
            }
        } catch (Exception exception) {
            log.warn("Failed to schedule notification for expired top-up {}: {}",
                    transaction.getPaymentCode(), exception.getMessage());
        }
    }

    private void notifyTopUpExpired(UUID userId, String paymentCode) {
        try {
            notificationService.push(
                    userId,
                    "Top-up expired",
                    "Top-up transaction " + paymentCode
                            + " expired before payment was confirmed.",
                    "PAYMENT_EXPIRED"
            );
        } catch (Exception exception) {
            // Notifications are best-effort and must not change payment state.
            log.warn("Failed to notify user about expired top-up {}: {}",
                    paymentCode, exception.getMessage());
        }
    }



    @Transactional
    public Transaction deductBalance(UUID userId, BigDecimal amount, TransactionType type, String description, UUID bookingId, UUID subscriptionId) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        getOrCreateWallet(userId);
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException(ErrorCode.INSUFFICIENT_BALANCE);
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





    @Transactional
    public Transaction refundBalance(UUID userId, BigDecimal amount, TransactionType type, String description, UUID bookingId, UUID subscriptionId) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
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

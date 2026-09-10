package fu.stockspace.stockspace_be.wallet.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.wallet.dto.TopUpRequest;
import fu.stockspace.stockspace_be.wallet.dto.TopUpResponse;
import fu.stockspace.stockspace_be.wallet.entity.PaymentMethod;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionStatus;
import fu.stockspace.stockspace_be.wallet.entity.TransactionType;
import fu.stockspace.stockspace_be.wallet.entity.Wallet;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTopUpLifecycleTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private fu.stockspace.stockspace_be.auth.repository.UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private VnPayService vnPayService;

    private WalletService walletService;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZONE);
        walletService = new WalletService(
                walletRepository,
                transactionRepository,
                userRepository,
                notificationService,
                vnPayService,
                clock);
        ReflectionTestUtils.setField(walletService, "topUpExpiryMinutes", 15L);
        ReflectionTestUtils.setField(walletService, "topUpExpiryGraceMinutes", 2L);

        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .balance(BigDecimal.ZERO)
                .build();
        User walletUser = org.mockito.Mockito.mock(User.class);
        org.mockito.Mockito.lenient().when(walletUser.getId()).thenReturn(UUID.randomUUID());
        wallet.setUser(walletUser);
    }

    @Test
    void createTopUpRequestStoresGatewayDeadline() {
        BigDecimal amount = new BigDecimal("100000");
        when(walletRepository.findByUserId(any())).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByPaymentCode(any())).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(vnPayService.createPaymentUrl(any(), eq(amount), eq("127.0.0.1")))
                .thenReturn("https://vnpay.test/pay");

        TopUpResponse response = walletService.createTopUpRequest(
                UUID.randomUUID(),
                TopUpRequest.builder().amount(amount).paymentMethod(PaymentMethod.VNPAY).build(),
                "127.0.0.1");

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(TransactionStatus.PENDING, captor.getValue().getStatus());
        assertEquals(LocalDateTime.ofInstant(NOW, ZONE).plusMinutes(15),
                captor.getValue().getExpiresAt());
        assertEquals(captor.getValue().getExpiresAt(), response.getExpiresAt());
    }

    @Test
    void expiryJobMarksOnlyOldPendingTopUps() {
        LocalDateTime expiresAt = LocalDateTime.ofInstant(NOW, ZONE).minusMinutes(18);
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(new BigDecimal("100000"))
                .transactionType(TransactionType.TOP_UP)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(TransactionStatus.PENDING)
                .paymentCode("STSP123456")
                .expiresAt(expiresAt)
                .build();
        when(transactionRepository.findExpiredPendingTopUpsForUpdate(
                LocalDateTime.ofInstant(NOW, ZONE).minusMinutes(2),
                LocalDateTime.ofInstant(NOW, ZONE).minusMinutes(17)))
                .thenReturn(List.of(transaction));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int expired = walletService.expirePendingTopUps();

        assertEquals(1, expired);
        assertEquals(TransactionStatus.EXPIRED, transaction.getStatus());
        verify(notificationService).push(
                any(), any(), any(), eq("PAYMENT_EXPIRED"));
    }

    @Test
    void lateSuccessfulCallbackCreditsAnExpiredTopUpOnce() {
        UUID userId = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(userId);
        wallet.setUser(user);
        wallet.setBalance(BigDecimal.ZERO);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(new BigDecimal("100000"))
                .transactionType(TransactionType.TOP_UP)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(TransactionStatus.EXPIRED)
                .paymentCode("STSP123456")
                .build();
        when(vnPayService.verifySignature(any())).thenReturn(true);
        when(transactionRepository.findByPaymentCodeForUpdate("STSP123456"))
                .thenReturn(Optional.of(transaction));
        when(walletRepository.findByUserIdWithLock(userId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        walletService.processVnPayPayment(Map.of(
                "vnp_TxnRef", "STSP123456",
                "vnp_ResponseCode", "00",
                "vnp_Amount", "10000000",
                "vnp_TransactionNo", "999999"));
        walletService.processVnPayPayment(Map.of(
                "vnp_TxnRef", "STSP123456",
                "vnp_ResponseCode", "00",
                "vnp_Amount", "10000000",
                "vnp_TransactionNo", "999999"));

        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(new BigDecimal("100000"), wallet.getBalance());
        verify(notificationService).push(eq(userId), any(), any(), eq("PAYMENT"));
    }

    @Test
    void callbackWithWrongAmountDoesNotCreditWallet() {
        UUID userId = UUID.randomUUID();
        User user = org.mockito.Mockito.mock(User.class);
        wallet.setUser(user);

        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(new BigDecimal("100000"))
                .transactionType(TransactionType.TOP_UP)
                .paymentMethod(PaymentMethod.VNPAY)
                .status(TransactionStatus.PENDING)
                .paymentCode("STSP123456")
                .build();
        when(vnPayService.verifySignature(any())).thenReturn(true);
        when(transactionRepository.findByPaymentCodeForUpdate("STSP123456"))
                .thenReturn(Optional.of(transaction));

        assertThrows(RuntimeException.class, () -> walletService.processVnPayPayment(Map.of(
                "vnp_TxnRef", "STSP123456",
                "vnp_ResponseCode", "00",
                "vnp_Amount", "20000000")));

        verify(walletRepository, never()).findByUserIdWithLock(userId);
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
    }
}

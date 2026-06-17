package fu.stockspace.stockspace_be.wallet.service;
import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.booking.entity.ApprovalStatus;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wallet.dto.WithdrawRequestDto;
import fu.stockspace.stockspace_be.wallet.dto.WithdrawResponse;
import fu.stockspace.stockspace_be.wallet.entity.*;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.repository.WalletRepository;
import fu.stockspace.stockspace_be.wallet.repository.WithdrawRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawService {
    private final WithdrawRequestRepository withdrawRequestRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    /**
     * Tạo yêu cầu rút tiền mới. Khấu trừ số dư ví ngay lập tức để tránh double spending.
     */
    @Transactional
    public WithdrawResponse submitWithdrawRequest(Long userId, WithdrawRequestDto dto) {
        // 1. Khóa ví kiểm tra số dư và trừ tiền
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        if (wallet.getBalance().compareTo(dto.getAmount()) < 0) {
            throw new BadRequestException(ErrorCode.WALLET_INSUFFICIENT_BALANCE);
        }
        wallet.setBalance(wallet.getBalance().subtract(dto.getAmount()));
        walletRepository.save(wallet);
        // 2. Tạo Transaction PENDING loại WITHDRAWAL
        Transaction transaction = Transaction.builder()
                .wallet(wallet)
                .amount(dto.getAmount())
                .transactionType(TransactionType.WITHDRAWAL)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .status(TransactionStatus.PENDING)
                .referenceId("SYS-WITHDRAW-PENDING-" + UUID.randomUUID())
                .build();
        transaction = transactionRepository.save(transaction);
        // 3. Tạo WithdrawRequest
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        WithdrawRequest request = WithdrawRequest.builder()
                .user(user)
                .transaction(transaction)
                .amount(dto.getAmount())
                .bankName(dto.getBankName())
                .bankAccountNumber(dto.getBankAccountNumber())
                .bankAccountHolder(dto.getBankAccountHolder())
                .status(ApprovalStatus.PENDING)
                .build();
        request = withdrawRequestRepository.save(request);
        log.info("Withdraw Service: User {} submitted withdraw request of {} VND. Wallet balance deducted.", userId, dto.getAmount());
        return mapToResponse(request);
    }
    /**
     * Lấy lịch sử yêu cầu rút tiền của chính user.
     */
    @Transactional(readOnly = true)
    public Page<WithdrawResponse> getMyWithdrawRequests(Long userId, Pageable pageable) {
        return withdrawRequestRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }
    /**
     * Lấy toàn bộ danh sách yêu cầu rút tiền (phục vụ Admin).
     */
    @Transactional(readOnly = true)
    public Page<WithdrawResponse> getAllWithdrawRequests(ApprovalStatus status, Pageable pageable) {
        if (status != null) {
            return withdrawRequestRepository.findByStatus(status, pageable)
                    .map(this::mapToResponse);
        }
        return withdrawRequestRepository.findAll(pageable)
                .map(this::mapToResponse);
    }
    /**
     * Admin duyệt yêu cầu rút tiền.
     */
    @Transactional
    public WithdrawResponse approveWithdraw(UUID requestId, String adminNotes) {
        WithdrawRequest request = withdrawRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WITHDRAW_REQUEST_NOT_FOUND));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.WITHDRAW_ALREADY_PROCESSED);
        }
        // Cập nhật trạng thái yêu cầu
        request.setStatus(ApprovalStatus.APPROVED);
        request.setAdminNotes(adminNotes);
        // Cập nhật trạng thái transaction thành SUCCESS
        Transaction transaction = request.getTransaction();
        if (transaction != null) {
            transaction.setStatus(TransactionStatus.SUCCESS);
            transaction.setReferenceId("SYS-WITHDRAW-SUCCESS-" + UUID.randomUUID());
            transactionRepository.save(transaction);
        }
        request = withdrawRequestRepository.save(request);
        log.info("Withdraw Service: Admin approved withdraw request ID: {}. Transaction confirmed.", requestId);
        return mapToResponse(request);
    }
    /**
     * Admin từ chối yêu cầu rút tiền. Hoàn trả lại tiền vào ví cho user.
     */
    @Transactional
    public WithdrawResponse rejectWithdraw(UUID requestId, String adminNotes) {
        WithdrawRequest request = withdrawRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WITHDRAW_REQUEST_NOT_FOUND));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new BadRequestException(ErrorCode.WITHDRAW_ALREADY_PROCESSED);
        }
        // Hoàn trả lại tiền vào ví
        Long userId = request.getUser().getId();
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WALLET_NOT_FOUND));
        wallet.setBalance(wallet.getBalance().add(request.getAmount()));
        walletRepository.save(wallet);
        // Cập nhật trạng thái yêu cầu
        request.setStatus(ApprovalStatus.REJECTED);
        request.setAdminNotes(adminNotes);
        // Cập nhật trạng thái transaction thành FAILED
        Transaction transaction = request.getTransaction();
        if (transaction != null) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setReferenceId("SYS-WITHDRAW-REJECTED-" + UUID.randomUUID());
            transactionRepository.save(transaction);
        }
        request = withdrawRequestRepository.save(request);
        log.info("Withdraw Service: Admin rejected withdraw request ID: {}. Wallet balance refunded.", requestId);
        return mapToResponse(request);
    }
    private WithdrawResponse mapToResponse(WithdrawRequest r) {
        return WithdrawResponse.builder()
                .id(r.getId())
                .userId(r.getUser().getId())
                .amount(r.getAmount())
                .bankName(r.getBankName())
                .bankAccountNumber(r.getBankAccountNumber())
                .bankAccountHolder(r.getBankAccountHolder())
                .status(r.getStatus())
                .adminNotes(r.getAdminNotes())
                .transactionId(r.getTransaction() != null ? r.getTransaction().getId() : null)
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
package fu.stockspace.stockspace_be.auth.controller;

import fu.stockspace.stockspace_be.wallet.service.WalletService;
import fu.stockspace.stockspace_be.wallet.service.VnPayService;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.entity.Transaction;
import fu.stockspace.stockspace_be.wallet.entity.TransactionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Public — VNPay Callback", description = "Endpoints công khai xử lý kết quả thanh toán từ VNPAY")
public class VnPayCallbackController {

    private final WalletService walletService;
    private final VnPayService vnPayService;
    private final TransactionRepository transactionRepository;

    @Value("${app.vnpay.frontend-callback-url:http://localhost:5173/wallet/callback}")
    private String frontendCallbackUrl;

    /**
     * GET /api/auth/vnpay-callback
     * Điểm nhận redirect từ VNPAY, cập nhật số dư ví và chuyển hướng người dùng về Frontend.
     */
    @GetMapping("/vnpay-callback")
    @Operation(summary = "Đón nhận chuyển hướng của người dùng từ VNPAY về Backend")
    public void handleVnPayCallback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Map<String, String> params = getParamsMap(request);
        log.info("VNPAY Redirect Callback received params: {}", params);

        String vnpResponseCode = params.get("vnp_ResponseCode");
        String paymentCode = params.get("vnp_TxnRef");
        
        String status = "fail";
        String amountStr = "0";

        try {
            // Thực hiện xử lý nạp tiền (có verify signature bên trong)
            walletService.processVnPayPayment(params);
            
            if ("00".equals(vnpResponseCode)) {
                status = "success";
                if (params.get("vnp_Amount") != null) {
                    BigDecimal amount = new BigDecimal(params.get("vnp_Amount")).divide(new BigDecimal(100));
                    amountStr = amount.toPlainString();
                }
            }
        } catch (Exception e) {
            log.error("Error processing VNPAY callback: {}", e.getMessage());
            status = "error";
        }

        // Chuyển hướng người dùng về Frontend với các query parameters
        String redirectUrl = String.format("%s?status=%s&amount=%s&code=%s", 
                frontendCallbackUrl, status, amountStr, paymentCode);
        response.sendRedirect(redirectUrl);
    }

    /**
     * GET /api/auth/vnpay-ipn
     * Nhận thông báo ngầm (IPN) từ VNPAY để cập nhật số dư ví an toàn.
     */
    @GetMapping("/vnpay-ipn")
    @Operation(summary = "Endpoint xử lý IPN trực tiếp từ server VNPAY")
    public ResponseEntity<Map<String, Object>> handleVnPayIpn(HttpServletRequest request) {
        Map<String, String> params = getParamsMap(request);
        log.info("VNPAY IPN Callback received params: {}", params);

        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Kiểm tra chữ ký
            if (!vnPayService.verifySignature(params)) {
                response.put("RspCode", "97");
                response.put("Message", "Invalid Checksum");
                return ResponseEntity.ok(response);
            }

            String paymentCode = params.get("vnp_TxnRef");
            String vnpResponseCode = params.get("vnp_ResponseCode");

            // 2. Kiểm tra đơn hàng có tồn tại không
            var transactionOpt = transactionRepository.findByPaymentCode(paymentCode);
            if (transactionOpt.isEmpty()) {
                response.put("RspCode", "01");
                response.put("Message", "Order not found");
                return ResponseEntity.ok(response);
            }

            Transaction transaction = transactionOpt.get();

            // 3. Kiểm tra số tiền có khớp không
            BigDecimal rawAmount = new BigDecimal(params.get("vnp_Amount"));
            BigDecimal vnpAmount = rawAmount.divide(new BigDecimal(100));
            if (transaction.getAmount().compareTo(vnpAmount) != 0) {
                response.put("RspCode", "04");
                response.put("Message", "Invalid Amount");
                return ResponseEntity.ok(response);
            }

            // 4. Kiểm tra trạng thái giao dịch (đã được confirm chưa)
            if (transaction.getStatus() != TransactionStatus.PENDING) {
                response.put("RspCode", "02");
                response.put("Message", "Order already confirmed");
                return ResponseEntity.ok(response);
            }

            // 5. Cập nhật trạng thái giao dịch & cộng tiền ví
            walletService.processVnPayPayment(params);

            response.put("RspCode", "00");
            response.put("Message", "Confirm Success");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Fatal error processing VNPAY IPN", e);
            response.put("RspCode", "99");
            response.put("Message", "Unknown Error: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }

    private Map<String, String> getParamsMap(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        }
        return params;
    }
}

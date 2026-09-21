package fu.stockspace.stockspace_be.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.stockspace.stockspace_be.wallet.repository.TransactionRepository;
import fu.stockspace.stockspace_be.wallet.service.PayOsService;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.payos.model.webhooks.WebhookData;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Public — PayOS Webhook & Callback", description = "Endpoints công khai xử lý kết quả thanh toán từ PayOS")
public class PayOsCallbackController {

    private final PayOsService payOsService;
    private final WalletService walletService;
    private final TransactionRepository transactionRepository;

    @Value("${app.payos.return-url:https://stock-space-nu.vercel.app/wallet/callback}")
    private String frontendCallbackUrl;

    /**
     * Webhook nhận thông báo biến động giao dịch tự động (Server-to-Server) từ PayOS.
     * PayOS gửi POST request kèm chữ ký HMAC SHA-256.
     */
    @PostMapping("/payos-webhook")
    @Operation(summary = "Endpoint xử lý Webhook thanh toán từ PayOS")
    public ResponseEntity<Map<String, Object>> handlePayOsWebhook(@RequestBody JsonNode webhookBody) {
        log.info("Received PayOS webhook payload: {}", webhookBody);

        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Xác thực chữ ký dữ liệu từ PayOS
            WebhookData webhookData = payOsService.verifyWebhook(webhookBody);
            Long orderCode = webhookData.getOrderCode();
            String paymentCode = String.valueOf(orderCode);

            // 2. Kiểm tra nếu là webhook test / ping từ PayOS dashboard khi đăng ký webhook URL
            boolean exists = transactionRepository.findByPaymentCode(paymentCode).isPresent();
            if (!exists) {
                String desc = webhookData.getDescription() != null ? webhookData.getDescription().toLowerCase() : "";
                if (orderCode == 123L || desc.contains("thu nghiem") || desc.contains("test")) {
                    log.info("PayOS webhook verification ping detected for dummy orderCode: {}. Responding 200 OK.", orderCode);
                    response.put("error", 0);
                    response.put("message", "Test webhook confirmed");
                    return ResponseEntity.ok(response);
                }
                log.warn("Transaction not found for PayOS orderCode: {}", orderCode);
                response.put("error", 0);
                response.put("message", "Order not found in system but webhook acknowledged");
                return ResponseEntity.ok(response);
            }

            // 3. Xử lý cộng tiền và cập nhật trạng thái đơn nạp
            walletService.processPayOsWebhook(webhookData);

            response.put("error", 0);
            response.put("message", "Webhook processed successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing PayOS webhook: {}", e.getMessage(), e);
            response.put("error", -1);
            response.put("message", e.getMessage());
            // Trả về 400 để PayOS ghi nhận lỗi xác thực nếu chữ ký sai
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Endpoint hỗ trợ trường hợp PayOS redirect người dùng về Backend.
     * Backend sẽ chuyển hướng tiếp về trang Frontend callback.
     */
    @GetMapping("/payos-callback")
    @Operation(summary = "Đón nhận chuyển hướng của người dùng từ PayOS về Backend và redirect sang Frontend")
    public void handlePayOsRedirect(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String cancel,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orderCode,
            HttpServletResponse response
    ) throws IOException {
        log.info("PayOS redirect callback received: code={}, id={}, cancel={}, status={}, orderCode={}",
                code, id, cancel, status, orderCode);

        String redirectStatus = "success";
        if ("true".equalsIgnoreCase(cancel) || "CANCELLED".equalsIgnoreCase(status)) {
            redirectStatus = "cancel";
        } else if (!"00".equals(code) && code != null) {
            redirectStatus = "fail";
        }

        String redirectUrl = String.format("%s?status=%s&code=%s",
                frontendCallbackUrl, redirectStatus, orderCode != null ? orderCode : "");
        response.sendRedirect(redirectUrl);
    }

    /**
     * Endpoint xác nhận URL webhook với PayOS (dùng khi cài đặt Webhook URL lần đầu).
     */
    @PostMapping("/payos/confirm-webhook")
    @Operation(summary = "Xác nhận Webhook URL với PayOS")
    public ResponseEntity<Map<String, Object>> confirmWebhook(@RequestParam String webhookUrl) {
        String result = payOsService.confirmWebhookUrl(webhookUrl);
        return ResponseEntity.ok(Map.of("error", 0, "message", result));
    }
}

package fu.stockspace.stockspace_be.auth.controller;
import fu.stockspace.stockspace_be.wallet.dto.SePayWebhookRequest;
import fu.stockspace.stockspace_be.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Slf4j
@RestController
@RequestMapping("/api/auth/sepay-webhook")
@RequiredArgsConstructor
@Tag(name = "Public — SePay Webhook", description = "Endpoint công khai nhận thông báo chuyển khoản tự động từ SePay")
public class SePayWebhookController {
    private final WalletService walletService;
    @PostMapping
    @Operation(summary = "Webhook tiếp nhận thông tin chuyển khoản từ SePay (Tự động nạp ví)")
    public ResponseEntity<Map<String, Object>> handleSePayWebhook(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SePayWebhookRequest payload) {
        
        log.info("Received SePay Webhook: id={}, amount={}, content='{}'", 
                payload.getId(), payload.getTransferAmount(), payload.getContent());
        walletService.processSePayWebhook(authHeader, payload);
        // SePay yêu cầu phản hồi HTTP 200 và JSON {"success": true}
        return ResponseEntity.ok(Map.of("success", true));
    }
}
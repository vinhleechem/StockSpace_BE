package fu.stockspace.stockspace_be.wallet.service;

import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.payos.PayOS;
import vn.payos.exception.PayOSException;
import vn.payos.exception.WebhookException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;

@Slf4j
@Service
public class PayOsService {

    @Value("${app.payos.client-id}")
    private String clientId;

    @Value("${app.payos.api-key}")
    private String apiKey;

    @Value("${app.payos.checksum-key}")
    private String checksumKey;

    @Value("${app.payos.return-url:https://stock-space-nu.vercel.app/wallet/callback}")
    private String returnUrl;

    @Value("${app.payos.cancel-url:https://stock-space-nu.vercel.app/wallet/callback?status=cancel}")
    private String cancelUrl;

    private PayOS payOS;

    @PostConstruct
    public void init() {
        if (clientId != null) clientId = clientId.trim();
        if (apiKey != null) apiKey = apiKey.trim();
        if (checksumKey != null) checksumKey = checksumKey.trim();
        if (returnUrl != null) returnUrl = returnUrl.trim();
        if (cancelUrl != null) cancelUrl = cancelUrl.trim();

        if (clientId != null && !clientId.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && checksumKey != null && !checksumKey.isBlank()) {
            this.payOS = new PayOS(clientId, apiKey, checksumKey);
            log.info("PayOsService initialized successfully with clientId: {}", clientId);
        } else {
            log.warn("PayOsService initialized with missing credentials. ClientId: {}", clientId);
        }
    }

    /**
     * Tạo link thanh toán PayOS.
     *
     * @param orderCode Mã đơn hàng (bắt buộc số nguyên long độc nhất)
     * @param amount    Số tiền giao dịch (VND)
     * @param description Mô tả đơn hàng (PayOS giới hạn tối đa 25 ký tự không dấu)
     * @param expiredAtEpochSeconds Thời gian hết hạn link tính bằng giây (epoch timestamp)
     * @return CreatePaymentLinkResponse chứa checkoutUrl và qrCode
     */
    public CreatePaymentLinkResponse createPaymentLink(Long orderCode, BigDecimal amount, String description, Long expiredAtEpochSeconds) {
        ensureInitialized();
        log.info("Creating PayOS payment link: orderCode={}, amount={}, description={}", orderCode, amount, description);

        String cleanDescription = sanitizeDescription(description, orderCode);

        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount(amount.longValue())
                .description(cleanDescription)
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .expiredAt(expiredAtEpochSeconds)
                .build();

        try {
            CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);
            log.info("PayOS payment link created successfully for orderCode {}: checkoutUrl={}, paymentLinkId={}",
                    orderCode, response.getCheckoutUrl(), response.getPaymentLinkId());
            return response;
        } catch (PayOSException e) {
            log.error("PayOS API error while creating payment link for orderCode {}: {}", orderCode, e.getMessage(), e);
            throw new BadRequestException("Lỗi từ cổng thanh toán PayOS: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while creating PayOS payment link for orderCode {}: {}", orderCode, e.getMessage(), e);
            throw new BadRequestException("Không thể tạo link thanh toán PayOS. Vui lòng thử lại sau.");
        }
    }

    /**
     * Xác thực chữ ký webhook nhận từ server PayOS.
     *
     * @param webhookBody Dữ liệu JSON hoặc Map gửi từ webhook PayOS
     * @return WebhookData đã được xác thực an toàn
     */
    public WebhookData verifyWebhook(Object webhookBody) {
        ensureInitialized();
        try {
            WebhookData data = payOS.webhooks().verify(webhookBody);
            log.info("PayOS webhook verified successfully for orderCode: {}", data.getOrderCode());
            return data;
        } catch (WebhookException e) {
            log.warn("PayOS webhook verification failed: {}", e.getMessage());
            throw new BadRequestException("Chữ ký bảo mật PayOS Webhook không hợp lệ: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error while verifying PayOS webhook: {}", e.getMessage(), e);
            throw new BadRequestException("Lỗi xác thực dữ liệu webhook từ PayOS: " + e.getMessage());
        }
    }

    /**
     * Lấy thông tin thanh toán trực tiếp từ PayOS theo orderCode (dùng để đối soát/fallback nếu cần).
     */
    public PaymentLink getPaymentLinkInformation(Long orderCode) {
        ensureInitialized();
        try {
            return payOS.paymentRequests().get(orderCode);
        } catch (Exception e) {
            log.warn("Failed to get payment link info from PayOS for orderCode {}: {}", orderCode, e.getMessage());
            return null;
        }
    }

    /**
     * Xác nhận Webhook URL với PayOS.
     */
    public String confirmWebhookUrl(String webhookUrl) {
        ensureInitialized();
        try {
            payOS.webhooks().confirm(webhookUrl);
            log.info("PayOS webhook URL confirmed successfully: {}", webhookUrl);
            return "Webhook URL confirmed successfully";
        } catch (Exception e) {
            log.error("Failed to confirm PayOS webhook URL {}: {}", webhookUrl, e.getMessage(), e);
            throw new BadRequestException("Không thể xác nhận Webhook URL với PayOS: " + e.getMessage());
        }
    }

    /**
     * PayOS giới hạn trường description tối đa 25 ký tự, không dấu, không ký tự đặc biệt.
     */
    private String sanitizeDescription(String description, Long orderCode) {
        if (description == null || description.isBlank()) {
            return "Nap vi " + orderCode;
        }
        String clean = description.trim();
        if (clean.length() > 25) {
            clean = clean.substring(0, 25).trim();
        }
        return clean;
    }

    private void ensureInitialized() {
        if (payOS == null) {
            throw new BadRequestException("Hệ thống thanh toán PayOS chưa được cấu hình Client ID / API Key hợp lệ.");
        }
    }
}

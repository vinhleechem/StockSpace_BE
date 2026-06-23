package fu.stockspace.stockspace_be.wallet.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VnPayServiceTest {

    private VnPayService vnPayService;

    @BeforeEach
    void setUp() {
        vnPayService = new VnPayService();
        // Cấu hình các thuộc tính @Value bằng ReflectionTestUtils
        ReflectionTestUtils.setField(vnPayService, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(vnPayService, "tmnCode", "2QXUIOJY");
        ReflectionTestUtils.setField(vnPayService, "hashSecret", "THCJDMMYVXPZNJTXHUPXKWJZXPEQJQUR");
        ReflectionTestUtils.setField(vnPayService, "returnUrl", "http://localhost:8080/api/auth/vnpay-callback");
    }

    @Test
    void testCreatePaymentUrl_Success() throws Exception {
        String txnRef = "STSP123456";
        BigDecimal amount = new BigDecimal("500000");
        String ipAddress = "127.0.0.1";

        String url = vnPayService.createPaymentUrl(txnRef, amount, ipAddress);

        assertNotNull(url);
        assertTrue(url.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"));
        assertTrue(url.contains("vnp_TmnCode=2QXUIOJY"));
        assertTrue(url.contains("vnp_TxnRef=" + txnRef));
        
        // 500000 * 100 = 50000000
        assertTrue(url.contains("vnp_Amount=50000000"));
        assertTrue(url.contains("vnp_OrderInfo=Nap%20tien%20vi%20StockSpace%20" + txnRef));
        assertFalse(url.contains("vnp_OrderInfo=Nap+tien+vi+StockSpace+" + txnRef));
        assertTrue(url.contains("vnp_SecureHash="));

        // Phân tích ngược các tham số từ URL
        String queryStr = url.substring(url.indexOf("?") + 1);
        String[] pairs = queryStr.split("&");
        Map<String, String> queryParams = new HashMap<>();
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            queryParams.put(key, value);
        }

        // Kiểm tra xem verifySignature có trả về true cho chính URL được sinh ra không
        assertTrue(vnPayService.verifySignature(queryParams));
    }

    @Test
    void testVerifySignature_InvalidSignature_ReturnsFalse() {
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_TxnRef", "STSP123456");
        fields.put("vnp_Amount", "50000000");
        fields.put("vnp_ResponseCode", "00");
        fields.put("vnp_TransactionNo", "999999");
        fields.put("vnp_SecureHash", "invalidhashvalue1234567890abcdef");

        assertFalse(vnPayService.verifySignature(fields));
    }
}

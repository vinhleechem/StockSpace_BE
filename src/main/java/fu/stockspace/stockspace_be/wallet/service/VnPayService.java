package fu.stockspace.stockspace_be.wallet.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VnPayService {

    @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${app.vnpay.tmn-code}")
    private String tmnCode;

    @Value("${app.vnpay.hash-secret}")
    private String hashSecret;

    @Value("${app.vnpay.return-url}")
    private String returnUrl;

    @PostConstruct
    public void init() {
        if (payUrl != null)
            this.payUrl = this.payUrl.trim();
        if (tmnCode != null)
            this.tmnCode = this.tmnCode.trim();
        if (hashSecret != null)
            this.hashSecret = this.hashSecret.trim();
        if (returnUrl != null)
            this.returnUrl = this.returnUrl.trim();
        log.info("VnPayService initialized. tmnCode: '{}', payUrl: '{}', returnUrl: '{}'", tmnCode, payUrl, returnUrl);
    }

    /**
     * Tạo URL thanh toán VNPAY
     *
     * @param txnRef    Mã giao dịch (paymentCode STSPxxxxxx)
     * @param amount    Số tiền nạp
     * @param ipAddress IP address của client
     * @return URL redirect sang VNPAY
     */
    public String createPaymentUrl(String txnRef, BigDecimal amount, String ipAddress) {
        log.info("Generating VNPAY payment URL for txnRef: {}, amount: {}, ipAddress: {}", txnRef, amount, ipAddress);

        // Chuẩn hóa IP: nếu là IPv6 loopback thì chuyển về IPv4
        String normalizedIp = normalizeIpAddress(ipAddress);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", tmnCode);

        // VNPAY yêu cầu số tiền nhân với 100 (ví dụ: 10000 VND thành 1000000)
        long vnpAmount = amount.multiply(new BigDecimal(100)).longValue();
        vnp_Params.put("vnp_Amount", String.valueOf(vnpAmount));
        vnp_Params.put("vnp_CurrCode", "VND");

        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_OrderInfo", "Nap tien vi StockSpace " + txnRef);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl);
        vnp_Params.put("vnp_IpAddr", normalizedIp);

        // FIX: Dùng Asia/Ho_Chi_Minh thay vì Etc/GMT+7
        // Etc/GMT+7 theo chuẩn POSIX là UTC-7 (ngược chiều), không phải UTC+7
        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        // Hạn thanh toán: +15 phút
        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Tạo queryUrl (encode cả key lẫn value) để gắn lên URL
        String queryUrl = buildQueryString(vnp_Params);

        // Tạo hashData (KHÔNG encode value) để tính chữ ký
        String hashData = buildHashData(vnp_Params);

        String vnp_SecureHash = hmacSHA512(hashSecret, hashData);
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;

        log.debug("VNPAY hashData: {}", hashData);
        log.debug("VNPAY secureHash: {}", vnp_SecureHash);

        return payUrl + "?" + queryUrl;
    }

    /**
     * Xác thực chữ ký VNPAY trả về
     */
    public boolean verifySignature(Map<String, String> fields) {
        String vnp_SecureHash = fields.get("vnp_SecureHash");
        if (vnp_SecureHash == null) {
            log.error("VNPAY signature verification failed: Missing vnp_SecureHash");
            return false;
        }

        // Loại bỏ tham số hash và hash type khỏi danh sách tính toán chữ ký
        Map<String, String> vnp_Params = new TreeMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) {
                vnp_Params.put(key, value);
            }
        }

        // FIX: Servlet đã decode params rồi → KHÔNG encode lại value khi tính hash
        // Dùng buildHashData thay vì getPaymentURL để tránh double-encode
        String hashData = buildHashData(vnp_Params);
        String computedHash = hmacSHA512(hashSecret, hashData);

        boolean matched = computedHash.equalsIgnoreCase(vnp_SecureHash);
        if (!matched) {
            log.warn("VNPAY signature mismatch. Computed: {}, Received: {}", computedHash, vnp_SecureHash);
            log.warn("Hash data used: {}", hashData);
        } else {
            log.info("VNPAY signature verified successfully.");
        }
        return matched;
    }

    /**
     * Tạo query string để gắn vào URL thanh toán.
     * Encode cả key lẫn value theo chuẩn URL.
     */
    public static String buildQueryString(Map<String, String> paramsMap) {
        return paramsMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    try {
                        return URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII.toString())
                                + "="
                                + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII.toString());
                    } catch (Exception e) {
                        return "";
                    }
                })
                .collect(Collectors.joining("&"));
    }

    /**
     * Tạo chuỗi dữ liệu để tính hash
     */
    public static String buildHashData(Map<String, String> paramsMap) {
        return paramsMap.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    try {
                        return entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII.toString());
                    } catch (Exception e) {
                        return "";
                    }
                })
                .collect(Collectors.joining("&"));
    }

    /**
     * Chuẩn hóa IP address.
     * VNPAY chỉ chấp nhận IPv4; nếu client dùng IPv6 loopback thì chuyển về
     * 127.0.0.1.
     */
    private String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "127.0.0.1";
        }
        // IPv6 loopback
        if ("::1".equals(ipAddress) || "0:0:0:0:0:0:0:1".equals(ipAddress)) {
            return "127.0.0.1";
        }
        // IPv6 mapped IPv4 (e.g. ::ffff:192.168.1.1)
        if (ipAddress.startsWith("::ffff:")) {
            return ipAddress.substring(7);
        }
        return ipAddress;
    }

    /**
     * Giải thuật băm HMAC SHA512
     */
    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Error computing HMAC SHA512", ex);
            return "";
        }
    }
}
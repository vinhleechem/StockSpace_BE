package fu.stockspace.stockspace_be.wallet.service;

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

@Slf4j
@Service
public class VnPayService {

    @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String payUrl;

    @Value("${app.vnpay.tmn-code:2QXUIOJY}")
    private String tmnCode;

    @Value("${app.vnpay.hash-secret:THCJDMMYVXPZNJTXHUPXKWJZXPEQJQUR}")
    private String hashSecret;

    @Value("${app.vnpay.return-url:http://localhost:3000/wallet/callback}")
    private String returnUrl;

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
        vnp_Params.put("vnp_IpAddr", ipAddress);

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnp_CreateDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_CreateDate", vnp_CreateDate);

        // Hạn thanh toán: +15 phút
        cld.add(Calendar.MINUTE, 15);
        String vnp_ExpireDate = formatter.format(cld.getTime());
        vnp_Params.put("vnp_ExpireDate", vnp_ExpireDate);

        // Sắp xếp các tham số theo thứ tự alphabet
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();

        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Thêm vào chuỗi băm
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(encode(fieldValue));

                // Thêm vào chuỗi query
                query.append(encode(fieldName));
                query.append('=');
                query.append(encode(fieldValue));

                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnp_SecureHash = hmacSHA512(hashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnp_SecureHash;

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
        Map<String, String> vnp_Params = new HashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) {
                vnp_Params.put(key, value);
            }
        }

        // Sắp xếp
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(encode(fieldValue));
                if (itr.hasNext()) {
                    hashData.append('&');
                }
            }
        }

        String computedHash = hmacSHA512(hashSecret, hashData.toString());
        boolean matched = computedHash.equalsIgnoreCase(vnp_SecureHash);
        if (!matched) {
            log.warn("VNPAY signature mismatch. Computed: {}, Received: {}", computedHash, vnp_SecureHash);
        }
        return matched;
    }

    /**
     * Mã hóa ký tự đặc biệt theo chuẩn RFC 3986 (sử dụng %20 thay vì + cho khoảng trắng)
     */
    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString()).replace("+", "%20");
        } catch (Exception e) {
            return "";
        }
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

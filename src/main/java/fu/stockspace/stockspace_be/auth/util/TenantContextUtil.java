package fu.stockspace.stockspace_be.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;
















public class TenantContextUtil {

    private TenantContextUtil() {

    }







    public static UUID getCurrentTenantId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            throw new IllegalStateException("TenantContextUtil: Không có request context hiện tại.");
        }
        HttpServletRequest request = attrs.getRequest();
        String tenantIdStr = (String) request.getAttribute("tenantId");
        if (tenantIdStr == null) {
            throw new IllegalStateException("TenantContextUtil: Không tìm thấy tenantId trong JWT. " +
                    "Đảm bảo endpoint được bảo vệ và JWT hợp lệ.");
        }
        return UUID.fromString(tenantIdStr);
    }
}

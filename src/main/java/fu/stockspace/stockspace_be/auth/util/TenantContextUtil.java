package fu.stockspace.stockspace_be.auth.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Utility tập trung giải quyết tenantId từ JWT claim trong mỗi request.
 *
 * Thay thế hoàn toàn pattern cũ:
 *   user.getTenant() != null ? user.getTenant().getId() : user.getId()
 *
 * Cách dùng trong Controller hoặc Service:
 *   UUID tenantId = TenantContextUtil.getCurrentTenantId();
 *
 * Nguyên tắc hoạt động:
 *   - JwtAuthFilter đọc claim "tenantId" từ JWT → lưu vào request attribute "tenantId".
 *   - Với Tenant/Owner/Admin: tenantId trong JWT = userId của chính họ.
 *   - Với Staff: tenantId trong JWT = UUID của Tenant mà Staff đang làm việc.
 *   → Không cần DB lookup thêm, không cần truyền thêm tham số.
 */
public class TenantContextUtil {

    private TenantContextUtil() {
        // Utility class — không instantiate
    }

    /**
     * Lấy tenantId đã được resolve sẵn từ JWT claim trong request hiện tại.
     *
     * @return UUID tenantId
     * @throws IllegalStateException nếu không có request context hoặc JWT không có claim tenantId
     */
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

package fu.stockspace.stockspace_be.common.entity;

import lombok.Getter;
import java.util.Optional;

@Getter
public enum SystemConfigKey {
    DEPOSIT_PERCENTAGE("deposit_percentage", "10", "Tỷ lệ phần trăm cọc thuê kho (ví dụ: 10 đại diện cho 10%)"),
    CONTRACT_EXPIRY_DAYS("contract_expiry_days", "7", "Số ngày tối đa để Tenant xác nhận ký hợp đồng online sau khi Owner submit"),
    INSPECTION_FEE("inspection_fee", "40000", "Phí gửi yêu cầu kiểm định kho bãi"),
    WAREHOUSE_PUBLISH_PACKAGE_ID("warehouse_publish_package_id", "1", "ID của gói dịch vụ Phí Đăng Bài Kho Bãi trong hệ thống");

    private final String key;
    private final String defaultValue;
    private final String defaultDescription;

    SystemConfigKey(String key, String defaultValue, String defaultDescription) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.defaultDescription = defaultDescription;
    }

    public static Optional<SystemConfigKey> fromKey(String key) {
        if (key == null) return Optional.empty();
        for (SystemConfigKey configKey : values()) {
            if (configKey.getKey().equalsIgnoreCase(key.trim())) {
                return Optional.of(configKey);
            }
        }
        return Optional.empty();
    }
}

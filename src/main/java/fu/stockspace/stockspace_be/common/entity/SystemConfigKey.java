package fu.stockspace.stockspace_be.common.entity;

import lombok.Getter;
import java.util.Optional;

@Getter
public enum SystemConfigKey {
    CONTRACT_EXPIRY_DAYS("contract_expiry_days", "7", "Số ngày tối đa để Tenant xác nhận ký hợp đồng online sau khi Owner submit", true),
    INSPECTION_FEE("inspection_fee", "40000", "Phí gửi yêu cầu kiểm định kho bãi", true);


    private final String key;
    private final String defaultValue;
    private final String defaultDescription;
    private final boolean isPublic;

    SystemConfigKey(String key, String defaultValue, String defaultDescription, boolean isPublic) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.defaultDescription = defaultDescription;
        this.isPublic = isPublic;
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

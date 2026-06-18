package fu.stockspace.stockspace_be.common.service;

import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;

    @Transactional(readOnly = true)
    public String getValue(String key, String defaultValue) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getIntValue(String key, int defaultValue) {
        try {
            return configRepository.findByConfigKey(key)
                    .map(c -> Integer.parseInt(c.getConfigValue().trim()))
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            log.error("Failed to parse integer config key '{}'", key, e);
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getBigDecimalValue(String key, BigDecimal defaultValue) {
        try {
            return configRepository.findByConfigKey(key)
                    .map(c -> new BigDecimal(c.getConfigValue().trim()))
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            log.error("Failed to parse BigDecimal config key '{}'", key, e);
            return defaultValue;
        }
    }

    @Transactional
    public void setValue(String key, String value, String description) {
        SystemConfig config = configRepository.findByConfigKey(key)
                .orElse(SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        if (description != null) {
            config.setDescription(description);
        }
        configRepository.save(config);
        log.info("SystemConfig updated: {} = {}", key, value);
    }
}

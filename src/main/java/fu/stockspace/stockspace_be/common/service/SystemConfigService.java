package fu.stockspace.stockspace_be.common.service;

import fu.stockspace.stockspace_be.common.dto.SystemConfigResponse;
import fu.stockspace.stockspace_be.common.dto.UpdateSystemConfigRequest;
import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.entity.SystemConfigKey;
import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import fu.stockspace.stockspace_be.subscription.repository.ServicePackageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository configRepository;
    private final ServicePackageRepository packageRepository;

    @Cacheable(value = "system_configs", key = "#key")
    @Transactional(readOnly = true)
    public String getValue(String key, String defaultValue) {
        Optional<SystemConfig> configOpt = configRepository.findByConfigKey(key);
        if (configOpt.isPresent()) {
            return configOpt.get().getConfigValue();
        }
        return SystemConfigKey.fromKey(key)
                .map(SystemConfigKey::getDefaultValue)
                .orElse(defaultValue);
    }

    @Cacheable(value = "system_configs", key = "#key")
    @Transactional(readOnly = true)
    public int getIntValue(String key, int defaultValue) {
        try {
            Optional<SystemConfig> configOpt = configRepository.findByConfigKey(key);
            if (configOpt.isPresent()) {
                return Integer.parseInt(configOpt.get().getConfigValue().trim());
            }
            return SystemConfigKey.fromKey(key)
                    .map(k -> Integer.parseInt(k.getDefaultValue().trim()))
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            log.error("Failed to parse integer config key '{}'", key, e);
            return defaultValue;
        }
    }

    @Cacheable(value = "system_configs", key = "#key")
    @Transactional(readOnly = true)
    public BigDecimal getBigDecimalValue(String key, BigDecimal defaultValue) {
        try {
            Optional<SystemConfig> configOpt = configRepository.findByConfigKey(key);
            if (configOpt.isPresent()) {
                return new BigDecimal(configOpt.get().getConfigValue().trim());
            }
            return SystemConfigKey.fromKey(key)
                    .map(k -> new BigDecimal(k.getDefaultValue().trim()))
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            log.error("Failed to parse BigDecimal config key '{}'", key, e);
            return defaultValue;
        }
    }

    @CacheEvict(value = "system_configs", key = "#key")
    @Transactional
    public void setValue(String key, String value, String description) {
        SystemConfig config = configRepository.findByConfigKey(key)
                .orElse(SystemConfig.builder().configKey(key).build());
        config.setConfigValue(value);
        if (description != null) {
            config.setDescription(description);
        }
        configRepository.save(config);
        log.info("SystemConfig updated and cache evicted: {} = {}", key, value);
    }

    @Transactional(readOnly = true)
    public List<SystemConfigResponse> getAllConfigs() {
        List<SystemConfigResponse> responses = new ArrayList<>();
        for (SystemConfigKey configKey : SystemConfigKey.values()) {
            Optional<SystemConfig> configOpt = configRepository.findByConfigKey(configKey.getKey());
            if (configOpt.isPresent()) {
                SystemConfig config = configOpt.get();
                responses.add(SystemConfigResponse.builder()
                        .id(config.getId())
                        .configKey(config.getConfigKey())
                        .configValue(config.getConfigValue())
                        .description(config.getDescription())
                        .updatedAt(config.getUpdatedAt() != null ? config.getUpdatedAt() : config.getCreatedAt())
                        .build());
            } else {
                responses.add(SystemConfigResponse.builder()
                        .id(null)
                        .configKey(configKey.getKey())
                        .configValue(configKey.getDefaultValue())
                        .description(configKey.getDefaultDescription())
                        .updatedAt(null)
                        .build());
            }
        }
        return responses;
    }

    @CacheEvict(value = "system_configs", key = "#key")
    @Transactional
    public SystemConfigResponse updateConfig(String key, UpdateSystemConfigRequest request) {
        SystemConfigKey configKey = SystemConfigKey.fromKey(key)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CONFIG_NOT_FOUND));

        validateValue(configKey, request.getConfigValue());

        SystemConfig config = configRepository.findByConfigKey(configKey.getKey())
                .orElse(SystemConfig.builder().configKey(configKey.getKey()).build());

        config.setConfigValue(request.getConfigValue().trim());
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        } else if (config.getDescription() == null) {
            config.setDescription(configKey.getDefaultDescription());
        }

        config = configRepository.save(config);
        log.info("SystemConfig updated via Admin API and cache evicted: {} = {}", configKey.getKey(), config.getConfigValue());

        return SystemConfigResponse.builder()
                .id(config.getId())
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .description(config.getDescription())
                .updatedAt(config.getUpdatedAt() != null ? config.getUpdatedAt() : config.getCreatedAt())
                .build();
    }

    private void validateValue(SystemConfigKey configKey, String value) {
        String key = configKey.getKey();
        if (key.equals("deposit_percentage")) {
            try {
                int val = Integer.parseInt(value.trim());
                if (val < 0 || val > 100) {
                    throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "Tỷ lệ phần trăm cọc phải nằm trong khoảng từ 0% đến 100%");
                }
            } catch (NumberFormatException e) {
                throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "Tỷ lệ phần trăm cọc phải là số nguyên");
            }
        } else if (key.equals("contract_expiry_days")) {
            try {
                int val = Integer.parseInt(value.trim());
                if (val <= 0) {
                    throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "Số ngày tối đa xác nhận hợp đồng phải lớn hơn 0");
                }
            } catch (NumberFormatException e) {
                throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "Số ngày tối đa xác nhận hợp đồng phải là số nguyên");
            }
        } else if (key.equals("inspection_fee")) {
            try {
                BigDecimal val = new BigDecimal(value.trim());
                if (val.compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "Phí gửi yêu cầu kiểm định không được là số âm");
                }
            } catch (NumberFormatException e) {
                throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "Phí gửi yêu cầu kiểm định phải là số thập phân hợp lệ");
            }
        } else if (key.equals("warehouse_publish_package_id")) {
            try {
                int val = Integer.parseInt(value.trim());
                if (val <= 0) {
                    throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "ID gói đăng bài phải là số nguyên lớn hơn 0");
                }
                if (!packageRepository.existsById(val)) {
                    throw new BadRequestException(ErrorCode.PACKAGE_NOT_FOUND, "Gói dịch vụ đăng bài không tồn tại trong hệ thống");
                }
            } catch (NumberFormatException e) {
                throw new BadRequestException(ErrorCode.CONFIG_INVALID_VALUE, "ID gói dịch vụ đăng bài phải là số nguyên");
            }
        }
    }
}

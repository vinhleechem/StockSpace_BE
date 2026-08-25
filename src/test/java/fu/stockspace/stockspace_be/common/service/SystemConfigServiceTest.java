package fu.stockspace.stockspace_be.common.service;

import fu.stockspace.stockspace_be.common.dto.SystemConfigResponse;
import fu.stockspace.stockspace_be.common.dto.UpdateSystemConfigRequest;
import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.entity.SystemConfigKey;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigRepository configRepository;

    @InjectMocks
    private SystemConfigService configService;

    private SystemConfig expiryConfig;
    private SystemConfig feeConfig;

    @BeforeEach
    void setUp() {
        expiryConfig = SystemConfig.builder()
                .id(UUID.randomUUID())
                .configKey(SystemConfigKey.CONTRACT_EXPIRY_DAYS.getKey())
                .configValue("14")
                .description("Contract confirmation window")
                .build();
        feeConfig = SystemConfig.builder()
                .id(UUID.randomUUID())
                .configKey(SystemConfigKey.INSPECTION_FEE.getKey())
                .configValue("50000")
                .description("Inspection fee")
                .build();
    }

    @Test
    void readsKnownValuesAndFallsBackToCanonicalDefaults() {
        when(configRepository.findByConfigKey(SystemConfigKey.CONTRACT_EXPIRY_DAYS.getKey()))
                .thenReturn(Optional.of(expiryConfig));
        when(configRepository.findByConfigKey(SystemConfigKey.INSPECTION_FEE.getKey()))
                .thenReturn(Optional.empty());

        assertEquals(14, configService.getIntValue(SystemConfigKey.CONTRACT_EXPIRY_DAYS.getKey(), 30));
        assertEquals(new BigDecimal("40000"), configService.getBigDecimalValue(
                SystemConfigKey.INSPECTION_FEE.getKey(), BigDecimal.ONE));
    }

    @Test
    void listsOnlyTheRemainingCanonicalConfigurationKeys() {
        when(configRepository.findByConfigKey(SystemConfigKey.CONTRACT_EXPIRY_DAYS.getKey()))
                .thenReturn(Optional.of(expiryConfig));
        when(configRepository.findByConfigKey(SystemConfigKey.INSPECTION_FEE.getKey()))
                .thenReturn(Optional.of(feeConfig));

        List<SystemConfigResponse> responses = configService.getPublicConfigs();

        assertEquals(2, responses.size());
        assertEquals(List.of("contract_expiry_days", "inspection_fee"),
                responses.stream().map(SystemConfigResponse::getConfigKey).toList());
    }

    @Test
    void updatesSupportedConfigurationAndRejectsInvalidValues() {
        when(configRepository.findByConfigKey(SystemConfigKey.INSPECTION_FEE.getKey()))
                .thenReturn(Optional.of(feeConfig));
        when(configRepository.save(any(SystemConfig.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SystemConfigResponse response = configService.updateConfig(
                SystemConfigKey.INSPECTION_FEE.getKey(),
                UpdateSystemConfigRequest.builder().configValue("45000.50").build());

        assertNotNull(response);
        assertEquals("45000.50", response.getConfigValue());
        assertThrows(BadRequestException.class, () -> configService.updateConfig(
                SystemConfigKey.CONTRACT_EXPIRY_DAYS.getKey(),
                UpdateSystemConfigRequest.builder().configValue("0").build()));
        assertThrows(ResourceNotFoundException.class, () -> configService.updateConfig(
                "deposit_percentage",
                UpdateSystemConfigRequest.builder().configValue("10").build()));
    }
}

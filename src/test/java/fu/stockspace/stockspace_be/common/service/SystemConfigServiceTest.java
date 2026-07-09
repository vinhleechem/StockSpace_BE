package fu.stockspace.stockspace_be.common.service;

import fu.stockspace.stockspace_be.common.dto.SystemConfigResponse;
import fu.stockspace.stockspace_be.common.dto.UpdateSystemConfigRequest;
import fu.stockspace.stockspace_be.common.entity.SystemConfig;
import fu.stockspace.stockspace_be.common.entity.SystemConfigKey;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.common.repository.SystemConfigRepository;
import fu.stockspace.stockspace_be.subscription.repository.ServicePackageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository configRepository;
    @Mock private ServicePackageRepository packageRepository;

    @InjectMocks
    private SystemConfigService configService;

    private SystemConfig depositConfig;
    private SystemConfig feeConfig;

    @BeforeEach
    void setUp() {
        depositConfig = SystemConfig.builder()
                .id(UUID.randomUUID())
                .configKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey())
                .configValue("15")
                .description("Custom deposit description")
                .build();

        feeConfig = SystemConfig.builder()
                .id(UUID.randomUUID())
                .configKey(SystemConfigKey.INSPECTION_FEE.getKey())
                .configValue("50000")
                .description("Custom inspection fee description")
                .build();
    }

    @Test
    void testGetValue_FallbackToEnumDefault() {
        when(configRepository.findByConfigKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .thenReturn(Optional.empty());

        String value = configService.getValue(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey(), "20");

        assertEquals(SystemConfigKey.DEPOSIT_PERCENTAGE.getDefaultValue(), value);
    }

    @Test
    void testGetValue_ReadFromRepository() {
        when(configRepository.findByConfigKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .thenReturn(Optional.of(depositConfig));

        String value = configService.getValue(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey(), "20");

        assertEquals("15", value);
    }

    @Test
    void testGetIntValue_FallbackToEnumDefault() {
        when(configRepository.findByConfigKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .thenReturn(Optional.empty());

        int value = configService.getIntValue(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey(), 20);

        assertEquals(10, value); // Default is 10
    }

    @Test
    void testGetBigDecimalValue_ReadFromRepository() {
        when(configRepository.findByConfigKey(SystemConfigKey.INSPECTION_FEE.getKey()))
                .thenReturn(Optional.of(feeConfig));

        BigDecimal value = configService.getBigDecimalValue(SystemConfigKey.INSPECTION_FEE.getKey(), new BigDecimal("40000"));

        assertEquals(new BigDecimal("50000"), value);
    }

    @Test
    void testGetAllConfigs_Success() {
        when(configRepository.findByConfigKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .thenReturn(Optional.of(depositConfig));
        when(configRepository.findByConfigKey(SystemConfigKey.INSPECTION_FEE.getKey()))
                .thenReturn(Optional.empty());
        when(configRepository.findByConfigKey(SystemConfigKey.CONTRACT_EXPIRY_DAYS.getKey()))
                .thenReturn(Optional.empty());
        when(configRepository.findByConfigKey(SystemConfigKey.WAREHOUSE_PUBLISH_PACKAGE_ID.getKey()))
                .thenReturn(Optional.empty());

        List<SystemConfigResponse> responses = configService.getAllConfigs();

        assertNotNull(responses);
        assertEquals(SystemConfigKey.values().length, responses.size());

        // Find deposit config in responses
        SystemConfigResponse depositResponse = responses.stream()
                .filter(r -> r.getConfigKey().equals(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .findFirst().orElse(null);

        assertNotNull(depositResponse);
        assertEquals("15", depositResponse.getConfigValue());
        assertEquals("Custom deposit description", depositResponse.getDescription());

        // Find inspection fee config in responses
        SystemConfigResponse feeResponse = responses.stream()
                .filter(r -> r.getConfigKey().equals(SystemConfigKey.INSPECTION_FEE.getKey()))
                .findFirst().orElse(null);

        assertNotNull(feeResponse);
        assertEquals(SystemConfigKey.INSPECTION_FEE.getDefaultValue(), feeResponse.getConfigValue());
    }

    @Test
    void testUpdateConfig_Success_DepositPercentage() {
        when(configRepository.findByConfigKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .thenReturn(Optional.of(depositConfig));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue("25")
                .description("Updated deposit description")
                .build();

        SystemConfigResponse response = configService.updateConfig(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey(), request);

        assertNotNull(response);
        assertEquals("25", response.getConfigValue());
        assertEquals("Updated deposit description", response.getDescription());
    }

    @Test
    void testUpdateConfig_InvalidValue_DepositPercentage_OutOfRange() {
        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue("150")
                .build();

        assertThrows(BadRequestException.class, () ->
                configService.updateConfig(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey(), request));
    }

    @Test
    void testUpdateConfig_InvalidValue_DepositPercentage_NotInteger() {
        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue("12.5")
                .build();

        assertThrows(BadRequestException.class, () ->
                configService.updateConfig(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey(), request));
    }

    @Test
    void testUpdateConfig_Success_InspectionFee() {
        when(configRepository.findByConfigKey(SystemConfigKey.INSPECTION_FEE.getKey()))
                .thenReturn(Optional.of(feeConfig));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue("45000.50")
                .build();

        SystemConfigResponse response = configService.updateConfig(SystemConfigKey.INSPECTION_FEE.getKey(), request);

        assertNotNull(response);
        assertEquals("45000.50", response.getConfigValue());
    }

    @Test
    void testUpdateConfig_InvalidValue_InspectionFee_Negative() {
        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue("-100")
                .build();

        assertThrows(BadRequestException.class, () ->
                configService.updateConfig(SystemConfigKey.INSPECTION_FEE.getKey(), request));
    }

    @Test
    void testUpdateConfig_Success_PackageId() {
        UUID validId = UUID.randomUUID();
        when(configRepository.findByConfigKey(SystemConfigKey.WAREHOUSE_PUBLISH_PACKAGE_ID.getKey()))
                .thenReturn(Optional.empty());
        when(packageRepository.existsById(validId)).thenReturn(true);
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue(validId.toString())
                .build();

        SystemConfigResponse response = configService.updateConfig(SystemConfigKey.WAREHOUSE_PUBLISH_PACKAGE_ID.getKey(), request);

        assertNotNull(response);
        assertEquals(validId.toString(), response.getConfigValue());
    }

    @Test
    void testUpdateConfig_PackageId_NotFound() {
        UUID nonExistentId = UUID.randomUUID();
        when(packageRepository.existsById(nonExistentId)).thenReturn(false);

        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue(nonExistentId.toString())
                .build();

        assertThrows(BadRequestException.class, () ->
                configService.updateConfig(SystemConfigKey.WAREHOUSE_PUBLISH_PACKAGE_ID.getKey(), request));
    }

    @Test
    void testUpdateConfig_PackageId_InvalidUUIDFormat() {
        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue("invalid-uuid-string")
                .build();

        assertThrows(BadRequestException.class, () ->
                configService.updateConfig(SystemConfigKey.WAREHOUSE_PUBLISH_PACKAGE_ID.getKey(), request));
    }

    @Test
    void testUpdateConfig_ConfigNotFound() {
        UpdateSystemConfigRequest request = UpdateSystemConfigRequest.builder()
                .configValue("any")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                configService.updateConfig("non_existent_key", request));
    }

    @Test
    void testGetPublicConfigs_Success() {
        when(configRepository.findByConfigKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .thenReturn(Optional.of(depositConfig));
        when(configRepository.findByConfigKey(SystemConfigKey.INSPECTION_FEE.getKey()))
                .thenReturn(Optional.of(feeConfig));
        when(configRepository.findByConfigKey(SystemConfigKey.CONTRACT_EXPIRY_DAYS.getKey()))
                .thenReturn(Optional.empty());

        List<SystemConfigResponse> responses = configService.getPublicConfigs();

        assertNotNull(responses);
        assertEquals(3, responses.size());

        boolean hasPackageId = responses.stream()
                .anyMatch(r -> r.getConfigKey().equals(SystemConfigKey.WAREHOUSE_PUBLISH_PACKAGE_ID.getKey()));
        assertFalse(hasPackageId);
    }

    @Test
    void testGetPublicConfigByKey_Success_PublicConfig() {
        when(configRepository.findByConfigKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey()))
                .thenReturn(Optional.of(depositConfig));

        SystemConfigResponse response = configService.getPublicConfigByKey(SystemConfigKey.DEPOSIT_PERCENTAGE.getKey());

        assertNotNull(response);
        assertEquals("15", response.getConfigValue());
    }

    @Test
    void testGetPublicConfigByKey_Failure_PrivateConfig() {
        assertThrows(ResourceNotFoundException.class, () ->
                configService.getPublicConfigByKey(SystemConfigKey.WAREHOUSE_PUBLISH_PACKAGE_ID.getKey()));
    }
}

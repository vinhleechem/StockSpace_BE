package fu.stockspace.stockspace_be.wms.product.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wms.product.dto.CreateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.dto.PagedSkuResponse;
import fu.stockspace.stockspace_be.wms.product.dto.ProductSkuResponse;
import fu.stockspace.stockspace_be.wms.product.dto.UpdateSkuRequest;
import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import fu.stockspace.stockspace_be.wms.product.entity.ProductSku;
import fu.stockspace.stockspace_be.wms.product.entity.UnitOfMeasure;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import fu.stockspace.stockspace_be.wms.product.repository.UnitOfMeasureRepository;
import fu.stockspace.stockspace_be.wms.stock.repository.StockBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductSkuServiceTest {

    @Mock
    private ProductSkuRepository skuRepository;

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private StockBatchRepository stockBatchRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UnitOfMeasureRepository uomRepository;

    @InjectMocks
    private ProductSkuService skuService;

    private UnitOfMeasure defaultUom;

    @BeforeEach
    void setUp() {
        defaultUom = UnitOfMeasure.builder()
                .id(UUID.randomUUID())
                .code("PCS")
                .name("Cái")
                .build();
    }

    @Test
    void testGetMySKUs_Success() {
        UUID tenantId = UUID.randomUUID();
        ProductSku sku = ProductSku.builder()
                .id(UUID.randomUUID())
                .skuCode("SKU123")
                .name("Test SKU")
                .uom(defaultUom)
                .tenant(User.builder().id(tenantId).build())
                .build();

        PageRequest pageRequest = PageRequest.of(0, 10);
        when(skuRepository.findAllActiveByTenantOrSystem(tenantId, pageRequest))
                .thenReturn(new PageImpl<>(Collections.singletonList(sku), pageRequest, 1));

        PagedSkuResponse response = skuService.getMySKUs(tenantId, pageRequest);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("SKU123", response.getContent().get(0).getSkuCode());
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void testGetSkuDetail_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .skuCode("SKU-OK")
                .uom(defaultUom)
                .tenant(User.builder().id(tenantId).build())
                .build();

        when(skuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));

        ProductSkuResponse response = skuService.getSkuDetail(tenantId, skuId);

        assertNotNull(response);
        assertEquals("SKU-OK", response.getSkuCode());
    }

    @Test
    void testGetSkuDetail_NotOwned_ThrowsForbidden() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .skuCode("SKU-OTHER")
                .uom(defaultUom)
                .tenant(User.builder().id(otherTenantId).build())
                .build();

        when(skuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));

        assertThrows(BadRequestException.class, () -> skuService.getSkuDetail(tenantId, skuId));
    }

    @Test
    void testCreateSku_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        ProductCategory category = ProductCategory.builder().id(catId).tenant(tenant).build();
        CreateSkuRequest request = CreateSkuRequest.builder()
                .categoryId(catId)
                .skuCode("NEW-SKU")
                .name("New product")
                .uomId(defaultUom.getId())
                .specifications(Collections.singletonMap("weight", "1kg"))
                .build();

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(categoryRepository.findByIdAndIsDeletedFalse(catId)).thenReturn(Optional.of(category));
        when(uomRepository.findByIdAndIsDeletedFalse(defaultUom.getId())).thenReturn(Optional.of(defaultUom));
        when(skuRepository.existsBySkuCodeAndTenantOrSystem("NEW-SKU", tenantId)).thenReturn(false);
        when(skuRepository.save(any(ProductSku.class))).thenAnswer(invocation -> {
            ProductSku sku = invocation.getArgument(0);
            sku.setId(UUID.randomUUID());
            return sku;
        });

        ProductSkuResponse response = skuService.createSku(tenantId, request);

        assertNotNull(response);
        assertEquals("NEW-SKU", response.getSkuCode());
        assertEquals("New product", response.getName());
        assertEquals(defaultUom.getId(), response.getUomId());
        assertEquals("PCS", response.getUomCode());
        assertEquals("1kg", response.getSpecifications().get("weight"));
        verify(skuRepository, times(1)).save(any(ProductSku.class));
    }

    @Test
    void testCreateSku_DuplicateSkuCode_ThrowsBadRequest() {
        UUID tenantId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        CreateSkuRequest request = CreateSkuRequest.builder()
                .skuCode("DUP-SKU")
                .name("Duplicate SKU")
                .uomId(defaultUom.getId())
                .build();

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(uomRepository.findByIdAndIsDeletedFalse(defaultUom.getId())).thenReturn(Optional.of(defaultUom));
        when(skuRepository.existsBySkuCodeAndTenantOrSystem("DUP-SKU", tenantId)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> skuService.createSku(tenantId, request));
        verify(skuRepository, never()).save(any(ProductSku.class));
    }

    @Test
    void testUpdateSku_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .skuCode("SKU-TO-UPDATE")
                .tenant(tenant)
                .name("Old Name")
                .uom(defaultUom)
                .build();

        UnitOfMeasure newUom = UnitOfMeasure.builder()
                .id(UUID.randomUUID())
                .code("BOX")
                .name("Thùng")
                .build();

        UpdateSkuRequest request = UpdateSkuRequest.builder()
                .name("New Name")
                .uomId(newUom.getId())
                .build();

        when(skuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(uomRepository.findByIdAndIsDeletedFalse(newUom.getId())).thenReturn(Optional.of(newUom));
        when(skuRepository.save(any(ProductSku.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductSkuResponse response = skuService.updateSku(tenantId, skuId, request);

        assertNotNull(response);
        assertEquals("New Name", response.getName());
        assertEquals(newUom.getId(), response.getUomId());
        assertEquals("BOX", response.getUomCode());
    }

    @Test
    void testUpdateSku_SystemSku_ThrowsForbidden() {
        UUID tenantId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        ProductSku systemSku = ProductSku.builder()
                .id(skuId)
                .skuCode("SYS-SKU")
                .tenant(null) // System SKU
                .name("System Sku Name")
                .uom(defaultUom)
                .build();
        UpdateSkuRequest request = UpdateSkuRequest.builder()
                .name("Try Update")
                .uomId(defaultUom.getId())
                .build();

        when(skuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(systemSku));

        assertThrows(BadRequestException.class, () -> skuService.updateSku(tenantId, skuId, request));
    }

    @Test
    void testDeleteSku_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .skuCode("SKU-TO-DELETE")
                .tenant(tenant)
                .uom(defaultUom)
                .build();

        when(skuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.existsBySkuIdAndIsDeletedFalse(skuId)).thenReturn(false);

        skuService.deleteSku(tenantId, skuId);

        assertTrue(sku.isDeleted());
        assertFalse(sku.isActive());
        verify(skuRepository, times(1)).save(sku);
    }

    @Test
    void testDeleteSku_InUse_ThrowsBadRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID skuId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        ProductSku sku = ProductSku.builder()
                .id(skuId)
                .skuCode("SKU-IN-USE")
                .tenant(tenant)
                .uom(defaultUom)
                .build();

        when(skuRepository.findByIdAndIsDeletedFalse(skuId)).thenReturn(Optional.of(sku));
        when(stockBatchRepository.existsBySkuIdAndIsDeletedFalse(skuId)).thenReturn(true); // Linked to stock batches

        assertThrows(BadRequestException.class, () -> skuService.deleteSku(tenantId, skuId));
        verify(skuRepository, never()).save(any(ProductSku.class));
    }
}

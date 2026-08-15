package fu.stockspace.stockspace_be.wms.product.service;

import fu.stockspace.stockspace_be.auth.entity.User;
import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import fu.stockspace.stockspace_be.wms.product.dto.CreateCategoryRequest;
import fu.stockspace.stockspace_be.wms.product.dto.ProductCategoryResponse;
import fu.stockspace.stockspace_be.wms.product.entity.ProductCategory;
import fu.stockspace.stockspace_be.wms.product.repository.ProductCategoryRepository;
import fu.stockspace.stockspace_be.wms.product.repository.ProductSkuRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceTest {

    @Mock
    private ProductCategoryRepository categoryRepository;

    @Mock
    private ProductSkuRepository skuRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductCategoryService categoryService;

    @Test
    void testGetMyCategories_Success() {
        UUID tenantId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder()
                .id(UUID.randomUUID())
                .name("Electronic")
                .tenant(User.builder().id(tenantId).build())
                .build();

        when(categoryRepository.findAllActiveByTenantOrSystem(tenantId))
                .thenReturn(Collections.singletonList(category));

        List<ProductCategoryResponse> result = categoryService.getMyCategories(tenantId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Electronic", result.get(0).getName());
        assertEquals(tenantId, result.get(0).getTenantId());
    }

    @Test
    void testCreateCategory_Success() {
        UUID tenantId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        CreateCategoryRequest request = CreateCategoryRequest.builder()
                .name("Dry Goods")
                .defaultAttributes(Collections.singletonMap("temp", "ambient"))
                .build();

        when(userRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(categoryRepository.save(any(ProductCategory.class))).thenAnswer(invocation -> {
            ProductCategory cat = invocation.getArgument(0);
            cat.setId(UUID.randomUUID());
            return cat;
        });

        ProductCategoryResponse response = categoryService.createCategory(tenantId, request);

        assertNotNull(response);
        assertEquals("Dry Goods", response.getName());
        assertEquals(tenantId, response.getTenantId());
        assertEquals("ambient", response.getDefaultAttributes().get("temp"));
        verify(categoryRepository, times(1)).save(any(ProductCategory.class));
    }

    @Test
    void testDeleteCategory_Success() {
        UUID tenantId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        User tenant = User.builder().id(tenantId).build();
        ProductCategory category = ProductCategory.builder()
                .id(catId)
                .tenant(tenant)
                .name("CatToDelete")
                .build();

        when(categoryRepository.findByIdAndIsDeletedFalse(catId)).thenReturn(Optional.of(category));
        when(skuRepository.existsByCategoryIdAndIsDeletedFalse(catId)).thenReturn(false);

        categoryService.deleteCategory(tenantId, catId);

        assertTrue(category.isDeleted());
        assertFalse(category.isActive());
        verify(categoryRepository, times(1)).save(category);
    }

    @Test
    void testDeleteCategory_SystemCategory_ThrowsForbidden() {
        UUID tenantId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        ProductCategory systemCategory = ProductCategory.builder()
                .id(catId)
                .tenant(null)
                .name("SystemCat")
                .build();

        when(categoryRepository.findByIdAndIsDeletedFalse(catId)).thenReturn(Optional.of(systemCategory));

        assertThrows(BadRequestException.class, () -> categoryService.deleteCategory(tenantId, catId));
        verify(categoryRepository, never()).save(any(ProductCategory.class));
    }

    @Test
    void testDeleteCategory_NotOwned_ThrowsForbidden() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        ProductCategory otherCategory = ProductCategory.builder()
                .id(catId)
                .tenant(User.builder().id(otherTenantId).build())
                .name("OtherCat")
                .build();

        when(categoryRepository.findByIdAndIsDeletedFalse(catId)).thenReturn(Optional.of(otherCategory));

        assertThrows(BadRequestException.class, () -> categoryService.deleteCategory(tenantId, catId));
        verify(categoryRepository, never()).save(any(ProductCategory.class));
    }

    @Test
    void testDeleteCategory_InUse_ThrowsBadRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID catId = UUID.randomUUID();
        ProductCategory category = ProductCategory.builder()
                .id(catId)
                .tenant(User.builder().id(tenantId).build())
                .name("InUseCat")
                .build();

        when(categoryRepository.findByIdAndIsDeletedFalse(catId)).thenReturn(Optional.of(category));
        when(skuRepository.existsByCategoryIdAndIsDeletedFalse(catId)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> categoryService.deleteCategory(tenantId, catId));
        verify(categoryRepository, never()).save(any(ProductCategory.class));
    }
}

package fu.stockspace.stockspace_be.contract.controller;

import fu.stockspace.stockspace_be.contract.dto.RentalContractResponse;
import fu.stockspace.stockspace_be.contract.entity.ContractStatus;
import fu.stockspace.stockspace_be.warehouse.dto.WarehouseResponse;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalRentalApiContractTest {

    @Test
    void contractStatusAndActionFlagSchemaMatchesTheFeHandoff() throws Exception {
        assertEquals(
                Set.of("DRAFT", "PENDING_TENANT_CONFIRM", "CHANGES_REQUESTED", "ACTIVE", "REJECTED", "EXPIRED"),
                Arrays.stream(ContractStatus.values()).map(Enum::name).collect(Collectors.toSet()));

        Set<String> fields = Arrays.stream(RentalContractResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertTrue(fields.containsAll(Set.of(
                "canEdit", "canDelete", "canSubmit", "canConfirm", "canRequestChanges",
                "canReject", "canViewLayout", "canManageWms")));

        Field files = RentalContractResponse.class.getDeclaredField("paperContractFiles");
        assertEquals(List.class, files.getType());
        assertTrue(files.getGenericType() instanceof ParameterizedType);
    }

    @Test
    void everyFinalContractEndpointHasAnOpenApiSummary() {
        assertDocumentedMappings(ContractController.class);
        assertDocumentedMappings(OwnerContractController.class);
        assertDocumentedMappings(TenantContractController.class);
    }

    @Test
    void publicWarehouseShapeCannotLeakOwnerPhone() {
        Set<String> fields = Arrays.stream(WarehouseResponse.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("ownerPhone"));
        assertFalse(fields.contains("phone"));
    }

    private void assertDocumentedMappings(Class<?> controllerType) {
        List<Method> mappedMethods = Arrays.stream(controllerType.getDeclaredMethods())
                .filter(this::isMapped)
                .toList();
        assertFalse(mappedMethods.isEmpty());
        mappedMethods.forEach(method -> {
            Operation operation = method.getAnnotation(Operation.class);
            assertNotNull(operation, controllerType.getSimpleName() + "." + method.getName());
            assertFalse(operation.summary().isBlank(), controllerType.getSimpleName() + "." + method.getName());
        });
    }

    private boolean isMapped(Method method) {
        return method.isAnnotationPresent(GetMapping.class)
                || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class);
    }
}

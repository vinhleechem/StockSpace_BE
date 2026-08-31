package fu.stockspace.stockspace_be.wms.receipt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryReceiptControllerContractTest {

    @Test
    void exposesOutboundReplanEndpointWithCreatePermission() throws NoSuchMethodException {
        Method method = InventoryReceiptController.class.getMethod(
                "replanOutboundReceipt", java.util.UUID.class);

        PostMapping mapping = method.getAnnotation(PostMapping.class);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertEquals("/{id}/picking/replan", mapping.value()[0]);
        assertEquals("@rbac.hasPermission('OUTBOUND_CREATE')", preAuthorize.value());
    }
}

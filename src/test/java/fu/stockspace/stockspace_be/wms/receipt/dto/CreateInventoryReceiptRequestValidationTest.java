package fu.stockspace.stockspace_be.wms.receipt.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType.INBOUND;
import static fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType.OUTBOUND;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateInventoryReceiptRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void inboundRequiresSenderNameOnly() {
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .type(INBOUND)
                .senderName(" ")
                .receiverName("")
                .build();

        assertTrue(hasViolation(request, "senderName"));
        assertFalse(hasViolation(request, "receiverName"));
    }

    @Test
    void outboundRequiresReceiverNameOnly() {
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .type(OUTBOUND)
                .senderName("")
                .receiverName(" ")
                .build();

        assertFalse(hasViolation(request, "senderName"));
        assertTrue(hasViolation(request, "receiverName"));
    }

    @Test
    void acceptsTheRequiredNameAndAllowsTheOtherNameToBeEmpty() {
        CreateInventoryReceiptRequest inbound = CreateInventoryReceiptRequest.builder()
                .type(INBOUND)
                .senderName("Supplier")
                .receiverName(null)
                .build();
        CreateInventoryReceiptRequest outbound = CreateInventoryReceiptRequest.builder()
                .type(OUTBOUND)
                .senderName(null)
                .receiverName("StockSpace")
                .build();

        assertFalse(hasViolation(inbound, "senderName"));
        assertFalse(hasViolation(inbound, "receiverName"));
        assertFalse(hasViolation(outbound, "senderName"));
        assertFalse(hasViolation(outbound, "receiverName"));
    }

    private boolean hasViolation(CreateInventoryReceiptRequest request, String property) {
        return validator.validate(request).stream()
                .anyMatch(violation -> property.equals(violation.getPropertyPath().toString()));
    }
}

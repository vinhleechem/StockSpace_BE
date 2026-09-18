package fu.stockspace.stockspace_be.wms.receipt.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
    void requiresSenderAndReceiverNames() {
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .senderName(" ")
                .receiverName("")
                .build();

        assertTrue(hasViolation(request, "senderName"));
        assertTrue(hasViolation(request, "receiverName"));
    }

    @Test
    void acceptsNonBlankSenderAndReceiverNamesWithinLimit() {
        CreateInventoryReceiptRequest request = CreateInventoryReceiptRequest.builder()
                .senderName("Supplier")
                .receiverName("StockSpace")
                .build();

        assertFalse(hasViolation(request, "senderName"));
        assertFalse(hasViolation(request, "receiverName"));
    }

    private boolean hasViolation(CreateInventoryReceiptRequest request, String property) {
        return validator.validate(request).stream()
                .anyMatch(violation -> property.equals(violation.getPropertyPath().toString()));
    }
}

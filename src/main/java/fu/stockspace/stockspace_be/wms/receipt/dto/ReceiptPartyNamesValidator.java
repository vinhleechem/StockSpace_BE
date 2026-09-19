package fu.stockspace.stockspace_be.wms.receipt.dto;

import fu.stockspace.stockspace_be.wms.receipt.entity.DocumentType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ReceiptPartyNamesValidator
        implements ConstraintValidator<ValidReceiptPartyNames, CreateInventoryReceiptRequest> {

    @Override
    public boolean isValid(CreateInventoryReceiptRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getType() == null) {
            return true;
        }

        String property;
        String value;
        String message;
        if (request.getType() == DocumentType.INBOUND) {
            property = "senderName";
            value = request.getSenderName();
            message = "Sender name is required for inbound receipts";
        } else {
            property = "receiverName";
            value = request.getReceiverName();
            message = "Receiver name is required for outbound receipts";
        }

        if (value != null && !value.isBlank()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
        return false;
    }
}

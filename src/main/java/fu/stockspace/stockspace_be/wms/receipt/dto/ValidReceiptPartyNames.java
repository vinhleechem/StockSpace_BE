package fu.stockspace.stockspace_be.wms.receipt.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = ReceiptPartyNamesValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidReceiptPartyNames {

    String message() default "Receipt party name is required for this receipt type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

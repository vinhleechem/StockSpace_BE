package fu.stockspace.stockspace_be.wallet.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletAmountValidationTest {

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
    void acceptsTopUpAndWithdrawAtOneHundredMillionVnd() {
        BigDecimal maximumAmount = new BigDecimal("100000000.00");

        assertFalse(hasAmountViolation(TopUpRequest.builder().amount(maximumAmount).build()));
        assertFalse(hasAmountViolation(WithdrawRequestDto.builder().amount(maximumAmount).build()));
    }

    @Test
    void rejectsTopUpAndWithdrawAboveOneHundredMillionVnd() {
        BigDecimal amountAboveMaximum = new BigDecimal("100000000.01");

        assertTrue(hasAmountViolation(TopUpRequest.builder().amount(amountAboveMaximum).build()));
        assertTrue(hasAmountViolation(WithdrawRequestDto.builder().amount(amountAboveMaximum).build()));
    }

    private boolean hasAmountViolation(Object request) {
        return validator.validate(request).stream()
                .anyMatch(violation -> "amount".equals(violation.getPropertyPath().toString()));
    }
}

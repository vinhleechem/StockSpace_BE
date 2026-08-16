package fu.stockspace.stockspace_be.common.validation;

public final class PhoneValidationPatterns {

    /** Vietnamese mobile number: 10 digits in local form or +84 form. */
    public static final String VIETNAMESE_MOBILE = "^(?:0[35789]\\d{8}|\\+84[35789]\\d{8})$";

    private PhoneValidationPatterns() {
    }
}

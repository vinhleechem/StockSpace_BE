package fu.stockspace.stockspace_be.common.validation;

import fu.stockspace.stockspace_be.admin.dto.CreateUserRequest;
import fu.stockspace.stockspace_be.admin.dto.UpdateUserRequest;
import fu.stockspace.stockspace_be.auth.dto.RegisterRequest;
import fu.stockspace.stockspace_be.auth.dto.UpdateProfileRequest;
import fu.stockspace.stockspace_be.auth.entity.RoleType;
import fu.stockspace.stockspace_be.staff.dto.InviteStaffRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneValidationPatternsTest {

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
    void acceptsOnlyValidVietnameseMobileFormats() {
        for (String phone : Set.of("0912345678", "0351234567", "+84912345678", "+84351234567")) {
            assertFalse(hasPhoneViolation(registerRequest(phone)));
            assertFalse(hasPhoneViolation(createUserRequest(phone)));
            assertFalse(hasPhoneViolation(updateUserRequest(phone)));
            assertFalse(hasPhoneViolation(updateProfileRequest(phone)));
            assertFalse(hasPhoneViolation(inviteStaffRequest(phone)));
        }
    }

    @Test
    void rejectsInvalidPhoneFormatsAtEveryEntryPoint() {
        for (String phone : Set.of("01234567890", "091234567", "09123456789", "+84012345678", "phone")) {
            assertTrue(hasPhoneViolation(registerRequest(phone)));
            assertTrue(hasPhoneViolation(createUserRequest(phone)));
            assertTrue(hasPhoneViolation(updateUserRequest(phone)));
            assertTrue(hasPhoneViolation(updateProfileRequest(phone)));
            assertTrue(hasPhoneViolation(inviteStaffRequest(phone)));
        }
    }

    private RegisterRequest registerRequest(String phone) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("tenant@example.com");
        request.setPassword("Password1");
        request.setFullName("Tenant Example");
        request.setRole(RoleType.ROLE_TENANT);
        request.setPhone(phone);
        return request;
    }

    private CreateUserRequest createUserRequest(String phone) {
        CreateUserRequest request = CreateUserRequest.builder()
                .email("admin-created@example.com")
                .password("Password1")
                .fullName("Admin Created")
                .roleIds(Set.of(UUID.randomUUID()))
                .phone(phone)
                .build();
        return request;
    }

    private UpdateUserRequest updateUserRequest(String phone) {
        return UpdateUserRequest.builder()
                .phone(phone)
                .build();
    }

    private UpdateProfileRequest updateProfileRequest(String phone) {
        return UpdateProfileRequest.builder()
                .fullName("Profile User")
                .phone(phone)
                .build();
    }

    private InviteStaffRequest inviteStaffRequest(String phone) {
        InviteStaffRequest request = new InviteStaffRequest();
        request.setEmail("staff@example.com");
        request.setFullName("Staff Example");
        request.setPhone(phone);
        return request;
    }

    private boolean hasPhoneViolation(Object request) {
        return validator.validate(request).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .anyMatch("phone"::equals);
    }
}

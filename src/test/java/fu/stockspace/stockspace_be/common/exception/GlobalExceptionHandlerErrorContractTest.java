package fu.stockspace.stockspace_be.common.exception;

import fu.stockspace.stockspace_be.common.dto.ApiResponse;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ForbiddenException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceConflictException;
import fu.stockspace.stockspace_be.common.exception.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerErrorContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void requiredRentalRefactorCodesHaveStableHttpStatuses() {
        Map<ErrorCode, HttpStatus> expected = Map.of(
                ErrorCode.WAREHOUSE_NOT_OWNED, HttpStatus.FORBIDDEN,
                ErrorCode.TENANT_NOT_FOUND, HttpStatus.NOT_FOUND,
                ErrorCode.INVALID_ROLE, HttpStatus.BAD_REQUEST,
                ErrorCode.CONTRACT_NOT_FOUND, HttpStatus.NOT_FOUND,
                ErrorCode.INVALID_CONTRACT_STATUS, HttpStatus.BAD_REQUEST,
                ErrorCode.CONTRACT_DATE_OVERLAP, HttpStatus.CONFLICT,
                ErrorCode.INVALID_LEASE_DIMENSIONS, HttpStatus.BAD_REQUEST,
                ErrorCode.SUBSCRIPTION_REQUIRED, HttpStatus.FORBIDDEN,
                ErrorCode.LISTING_PACKAGE_INACTIVE, HttpStatus.BAD_REQUEST,
                ErrorCode.INSUFFICIENT_BALANCE, HttpStatus.BAD_REQUEST
        );

        expected.forEach((code, status) -> assertEquals(status, code.getStatus(), code.name()));
    }

    @Test
    void typedExceptionsExposeMachineReadableCodesInTheApiEnvelope() {
        assertError(
                handler.handleBadRequest(new BadRequestException(ErrorCode.INVALID_CONTRACT_STATUS)),
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_CONTRACT_STATUS);
        assertError(
                handler.handleForbidden(new ForbiddenException(ErrorCode.SUBSCRIPTION_REQUIRED)),
                HttpStatus.FORBIDDEN,
                ErrorCode.SUBSCRIPTION_REQUIRED);
        assertError(
                handler.handleResourceNotFound(new ResourceNotFoundException(ErrorCode.CONTRACT_NOT_FOUND)),
                HttpStatus.NOT_FOUND,
                ErrorCode.CONTRACT_NOT_FOUND);
        assertError(
                handler.handleResourceConflict(new ResourceConflictException(ErrorCode.CONTRACT_DATE_OVERLAP)),
                HttpStatus.CONFLICT,
                ErrorCode.CONTRACT_DATE_OVERLAP);
    }

    @Test
    void successEnvelopeDoesNotEmitAnErrorCode() {
        assertNull(ApiResponse.success("ok").getCode());
    }

    private void assertError(ResponseEntity<ApiResponse<Void>> response,
                             HttpStatus expectedStatus,
                             ErrorCode expectedCode) {
        assertEquals(expectedStatus, response.getStatusCode());
        assertEquals(expectedCode.name(), response.getBody().getCode());
        assertEquals(false, response.getBody().isSuccess());
    }
}

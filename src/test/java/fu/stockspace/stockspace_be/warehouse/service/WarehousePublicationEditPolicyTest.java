package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarehousePublicationEditPolicyTest {

    private static final Clock PUBLICATION_CLOCK = Clock.fixed(
            Instant.parse("2026-08-31T01:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    private final WarehousePublicationEditPolicy policy =
            new WarehousePublicationEditPolicy(PUBLICATION_CLOCK);

    @Test
    void approvedWarehouseWithoutOpenPublicationLosesApprovalAfterEdit() {
        Warehouse warehouse = Warehouse.builder()
                .status(WarehouseStatus.AVAILABLE)
                .publishedAt(null)
                .visibleUntil(null)
                .rejectReason("old reason")
                .build();

        boolean approvalRequired = policy.prepareOwnerEdit(warehouse);

        assertEquals(true, approvalRequired);
        assertEquals(WarehouseStatus.PENDING_APPROVAL, warehouse.getStatus());
        assertEquals(null, warehouse.getPublishedAt());
        assertEquals(null, warehouse.getVisibleUntil());
        assertEquals(null, warehouse.getRejectReason());
    }

    @Test
    void scheduledOrActivePublicationCannotBeEdited() {
        Warehouse warehouse = Warehouse.builder()
                .status(WarehouseStatus.AVAILABLE)
                .visibleUntil(java.time.LocalDateTime.now(PUBLICATION_CLOCK).plusDays(1))
                .build();

        BadRequestException exception = assertThrows(BadRequestException.class,
                () -> policy.prepareOwnerEdit(warehouse));

        assertEquals(ErrorCode.LISTING_PUBLICATION_ACTION_NOT_ALLOWED, exception.getErrorCode());
        assertEquals(WarehouseStatus.AVAILABLE, warehouse.getStatus());
    }

    @Test
    void nonApprovedWarehouseCanBeEditedWithoutChangingItsLifecycleState() {
        Warehouse warehouse = Warehouse.builder()
                .status(WarehouseStatus.DRAFT)
                .build();

        boolean approvalRequired = policy.prepareOwnerEdit(warehouse);

        assertEquals(false, approvalRequired);
        assertEquals(WarehouseStatus.DRAFT, warehouse.getStatus());
    }
}

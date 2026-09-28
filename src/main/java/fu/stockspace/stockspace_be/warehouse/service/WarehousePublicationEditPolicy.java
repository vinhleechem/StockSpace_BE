package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.common.exception.ErrorCode;
import fu.stockspace.stockspace_be.common.exception.exceptions.BadRequestException;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import fu.stockspace.stockspace_be.warehouse.entity.WarehouseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class WarehousePublicationEditPolicy {

    private final Clock publicationClock;

    public boolean prepareOwnerEdit(Warehouse warehouse) {
        if (warehouse.getStatus() != WarehouseStatus.AVAILABLE) {
            return false;
        }

        assertOwnerEditAllowed(warehouse);

        warehouse.setStatus(WarehouseStatus.PENDING_APPROVAL);
        warehouse.setRejectReason(null);
        warehouse.setPublishedAt(null);
        warehouse.setVisibleUntil(null);
        return true;
    }

    public void assertOwnerEditAllowed(Warehouse warehouse) {
        if (warehouse.getStatus() != WarehouseStatus.AVAILABLE) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(publicationClock);
        if (warehouse.getVisibleUntil() != null && warehouse.getVisibleUntil().isAfter(now)) {
            throw new BadRequestException(ErrorCode.LISTING_PUBLICATION_ACTION_NOT_ALLOWED);
        }
    }
}

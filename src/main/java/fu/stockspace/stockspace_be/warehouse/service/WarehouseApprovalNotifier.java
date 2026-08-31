package fu.stockspace.stockspace_be.warehouse.service;

import fu.stockspace.stockspace_be.auth.repository.UserRepository;
import fu.stockspace.stockspace_be.notification.service.NotificationService;
import fu.stockspace.stockspace_be.warehouse.entity.Warehouse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Sends the single Admin notification used by owner approval submissions. */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseApprovalNotifier {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public void notifyAdmin(Warehouse warehouse) {
        try {
            userRepository.findFirstByRoles_Name("ROLE_ADMIN")
                    .ifPresent(admin -> notificationService.push(
                            admin.getId(),
                            "Warehouse listing awaiting review",
                            "Owner submitted warehouse '" + warehouse.getName()
                                    + "' for content approval.",
                            "WAREHOUSE"));
        } catch (Exception exception) {
            log.warn("Failed to push warehouse approval notification: {}", exception.getMessage());
        }
    }
}

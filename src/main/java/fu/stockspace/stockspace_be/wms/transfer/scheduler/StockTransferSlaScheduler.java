package fu.stockspace.stockspace_be.wms.transfer.scheduler;

import fu.stockspace.stockspace_be.wms.transfer.service.StockTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically exposes transfers that are silent past their expected arrival time. */
@Component
@RequiredArgsConstructor
@Slf4j
public class StockTransferSlaScheduler {

    private final StockTransferService stockTransferService;

    @Scheduled(fixedDelayString = "${stock.transfer.sla.check-ms:900000}")
    public void markOverdueTransfers() {
        int count = stockTransferService.markOverdueTransfers();
        if (count > 0) {
            log.info("Marked {} stock transfer(s) as OVERDUE", count);
        }
    }
}

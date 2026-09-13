package fu.stockspace.stockspace_be.wms.dataexchange.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WmsImportRowRepository extends JpaRepository<WmsImportRow, UUID> {

    List<WmsImportRow> findByJobIdOrderBySheetNameAscRowNumberAsc(UUID jobId);

    List<WmsImportRow> findByJobIdAndGroupKeyOrderByRowNumberAsc(UUID jobId, String groupKey);
}

package fu.stockspace.stockspace_be.listing.repository;

import fu.stockspace.stockspace_be.listing.entity.ListingPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListingPackageRepository extends JpaRepository<ListingPackage, UUID> {

    List<ListingPackage> findAllByIsActiveTrueAndIsDeletedFalseOrderByDurationDaysAsc();

    List<ListingPackage> findAllByOrderByDurationDaysAsc();

    Optional<ListingPackage> findByDurationDays(Integer durationDays);

    boolean existsByDurationDaysAndIdNot(Integer durationDays, UUID id);
}

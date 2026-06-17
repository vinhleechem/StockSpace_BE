package fu.stockspace.stockspace_be.subscription.repository;
import fu.stockspace.stockspace_be.subscription.entity.ServicePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Integer> {
    Optional<ServicePackage> findByName(String name);
}
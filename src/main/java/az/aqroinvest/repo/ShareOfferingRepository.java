package az.aqroinvest.repo;

import az.aqroinvest.domain.ShareOffering;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareOfferingRepository extends JpaRepository<ShareOffering, Long> {
}

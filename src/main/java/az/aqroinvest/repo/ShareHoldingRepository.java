package az.aqroinvest.repo;

import az.aqroinvest.domain.ShareHolding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareHoldingRepository extends JpaRepository<ShareHolding, Long> {
}

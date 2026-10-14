package az.aqroinvest.repo;

import az.aqroinvest.domain.WorkforceItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkforceItemRepository extends JpaRepository<WorkforceItem, Long> {
}

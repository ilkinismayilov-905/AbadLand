package az.aqroinvest.repo;

import az.aqroinvest.domain.CropPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropPlanItemRepository extends JpaRepository<CropPlanItem, Long> {
}

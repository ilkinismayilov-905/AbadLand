package az.aqroinvest.repo;

import az.aqroinvest.domain.CropPlanItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropPlanItemRepository extends JpaRepository<CropPlanItem, Long> {
    java.util.List<CropPlanItem> findByProjectOrderByIdAsc(az.aqroinvest.domain.FarmProject project);
}

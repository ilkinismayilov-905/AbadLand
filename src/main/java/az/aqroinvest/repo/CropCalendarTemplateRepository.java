package az.aqroinvest.repo;

import az.aqroinvest.domain.CropCalendarTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropCalendarTemplateRepository extends JpaRepository<CropCalendarTemplate, Long> {
    java.util.List<CropCalendarTemplate> findByCrop(az.aqroinvest.domain.Crop crop);
}

package az.aqroinvest.repo;

import az.aqroinvest.domain.Crop;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropRepository extends JpaRepository<Crop, Long> {
    java.util.Optional<Crop> findByCode(String code);
}

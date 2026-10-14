package az.aqroinvest.repo;

import az.aqroinvest.domain.Plot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlotRepository extends JpaRepository<Plot, Long> {
    java.util.Optional<Plot> findByCode(String code);
}

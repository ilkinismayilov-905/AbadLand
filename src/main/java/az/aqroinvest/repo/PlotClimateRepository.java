package az.aqroinvest.repo;

import az.aqroinvest.domain.PlotClimate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlotClimateRepository extends JpaRepository<PlotClimate, Long> {
    java.util.Optional<PlotClimate> findByPlot(az.aqroinvest.domain.Plot plot);
}

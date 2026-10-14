package az.aqroinvest.repo;

import az.aqroinvest.domain.PlotCropSuitability;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlotCropSuitabilityRepository extends JpaRepository<PlotCropSuitability, Long> {
    java.util.List<PlotCropSuitability> findByPlot(az.aqroinvest.domain.Plot plot);
    void deleteByPlotAndSource(az.aqroinvest.domain.Plot plot, az.aqroinvest.domain.SuitabilitySource source);
}

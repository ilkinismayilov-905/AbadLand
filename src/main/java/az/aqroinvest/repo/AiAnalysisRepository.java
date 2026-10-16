package az.aqroinvest.repo;

import az.aqroinvest.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {
    java.util.Optional<AiAnalysis> findFirstByPlotAndStatusOrderByCreatedAtDesc(az.aqroinvest.domain.Plot plot, String status);

    java.util.Optional<AiAnalysis> findFirstByModelAndStatusOrderByCreatedAtDesc(String model, String status);

    /** One row per analysis without the result JSON (AI center state badges). */
    @Query("select a.plot.id as plotId, a.model as model, a.status as status from AiAnalysis a")
    java.util.List<StateRow> stateRows();

    interface StateRow {
        Long getPlotId();

        String getModel();

        String getStatus();
    }
}

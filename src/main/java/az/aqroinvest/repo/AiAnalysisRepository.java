package az.aqroinvest.repo;

import az.aqroinvest.domain.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {
    java.util.Optional<AiAnalysis> findFirstByPlotAndStatusOrderByCreatedAtDesc(az.aqroinvest.domain.Plot plot, String status);
}

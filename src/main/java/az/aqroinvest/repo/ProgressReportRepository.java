package az.aqroinvest.repo;

import az.aqroinvest.domain.ProgressReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProgressReportRepository extends JpaRepository<ProgressReport, Long> {
    java.util.List<ProgressReport> findByProjectOrderBySubmittedAtDesc(az.aqroinvest.domain.FarmProject project);
}

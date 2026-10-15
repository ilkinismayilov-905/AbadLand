package az.aqroinvest.repo;

import az.aqroinvest.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    java.util.List<Document> findByApplicationOrderByUploadedAtAsc(az.aqroinvest.domain.Application application);
    java.util.List<Document> findByReportOrderByIdAsc(az.aqroinvest.domain.ProgressReport report);
    java.util.List<Document> findByPlotOrderBySortOrderAscIdAsc(az.aqroinvest.domain.Plot plot);
}

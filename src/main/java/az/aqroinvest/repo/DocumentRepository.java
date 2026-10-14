package az.aqroinvest.repo;

import az.aqroinvest.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    java.util.List<Document> findByApplicationOrderByUploadedAtAsc(az.aqroinvest.domain.Application application);
}

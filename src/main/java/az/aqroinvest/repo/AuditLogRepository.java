package az.aqroinvest.repo;

import az.aqroinvest.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    java.util.List<AuditLog> findTop200ByOrderByAtDescIdDesc();
    long countByActorEmailIgnoreCaseAndActionAndAtAfter(String actorEmail, String action, java.time.LocalDateTime at);
}

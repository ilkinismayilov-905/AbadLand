package az.aqroinvest.repo;

import az.aqroinvest.domain.IntegrationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationLogRepository extends JpaRepository<IntegrationLog, Long> {
    java.util.List<IntegrationLog> findAllByOrderByAtDescIdDesc();
}

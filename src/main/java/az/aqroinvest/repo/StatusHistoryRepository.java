package az.aqroinvest.repo;

import az.aqroinvest.domain.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    java.util.List<StatusHistory> findByApplicationOrderByAtAscIdAsc(az.aqroinvest.domain.Application application);
}

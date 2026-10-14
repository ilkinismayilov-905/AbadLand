package az.aqroinvest.repo;

import az.aqroinvest.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    java.util.List<Application> findByDecision(az.aqroinvest.domain.Decision decision);
    java.util.List<Application> findByInvestorEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}

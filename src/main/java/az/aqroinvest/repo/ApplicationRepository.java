package az.aqroinvest.repo;

import az.aqroinvest.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    java.util.List<Application> findByDecision(az.aqroinvest.domain.Decision decision);
    java.util.List<Application> findByInvestorEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    java.util.List<Application> findAllByOrderByUpdatedAtDesc();
    java.util.List<Application> findByStatusOrderByUpdatedAtDesc(az.aqroinvest.domain.ApplicationStatus status);
    @org.springframework.data.jpa.repository.Query("select max(a.number) from Application a where a.number like concat(:prefix, '%')")
    String maxNumberWithPrefix(@org.springframework.data.repository.query.Param("prefix") String prefix);
    java.util.Optional<Application> findByNumber(String number);
}

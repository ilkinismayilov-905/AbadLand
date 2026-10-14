package az.aqroinvest.repo;

import az.aqroinvest.domain.FarmProject;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FarmProjectRepository extends JpaRepository<FarmProject, Long> {
    java.util.List<FarmProject> findByInvestorEmailIgnoreCaseOrderByIdDesc(String email);
    java.util.Optional<FarmProject> findByApplication(az.aqroinvest.domain.Application application);
    java.util.List<FarmProject> findAllByOrderByIdDesc();
}

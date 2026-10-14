package az.aqroinvest.repo;

import az.aqroinvest.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    long countByStatus(az.aqroinvest.domain.CompetitionStatus status);
}

package az.aqroinvest.repo;

import az.aqroinvest.domain.Competition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {
    long countByStatus(az.aqroinvest.domain.CompetitionStatus status);
    java.util.List<Competition> findByStatusOrderByDeadlineAsc(az.aqroinvest.domain.CompetitionStatus status);
    java.util.List<Competition> findAllByOrderByIdDesc();
    @org.springframework.data.jpa.repository.Query("select max(c.code) from Competition c where c.code like concat(:prefix, '%')")
    String maxCodeWithPrefix(@org.springframework.data.repository.query.Param("prefix") String prefix);
}

package az.aqroinvest.repo;

import az.aqroinvest.domain.CalendarTask;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarTaskRepository extends JpaRepository<CalendarTask, Long> {
}

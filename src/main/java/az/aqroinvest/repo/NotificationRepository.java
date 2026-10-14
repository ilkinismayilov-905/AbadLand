package az.aqroinvest.repo;

import az.aqroinvest.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    long countByUserEmailIgnoreCaseAndReadFalse(String email);
}

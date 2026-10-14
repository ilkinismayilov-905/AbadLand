package az.aqroinvest.service;

import az.aqroinvest.domain.Notification;
import az.aqroinvest.domain.Role;
import az.aqroinvest.domain.User;
import az.aqroinvest.repo.NotificationRepository;
import az.aqroinvest.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static az.aqroinvest.service.AuditService.BAKU;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repo;
    private final UserRepository users;

    public void notify(User user, String message, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message);
        n.setLink(link);
        n.setAt(LocalDateTime.now(BAKU));
        repo.save(n);
    }

    public void notifyRole(Role role, String message, String link) {
        users.findByRole(role).forEach(u -> notify(u, message, link));
    }

    public List<Notification> list(String email) {
        return repo.findByUserEmailIgnoreCaseOrderByAtDescIdDesc(email);
    }

    @Transactional
    public void markAllRead(String email) {
        repo.findByUserEmailIgnoreCaseAndReadFalse(email).forEach(n -> n.setRead(true));
    }
}

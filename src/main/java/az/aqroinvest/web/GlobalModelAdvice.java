package az.aqroinvest.web;

import az.aqroinvest.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds the unread-notification count used by the header badge. */
@ControllerAdvice(annotations = org.springframework.stereotype.Controller.class)
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final NotificationRepository notifications;

    @ModelAttribute("unreadCount")
    public long unreadCount(Authentication auth) {
        return auth == null ? 0 : notifications.countByUserEmailIgnoreCaseAndReadFalse(auth.getName());
    }
}

package az.aqroinvest.web;

import az.aqroinvest.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;

    /** Shows the list (unread highlighted), then marks everything read. */
    @GetMapping("/bildirisler")
    public String list(Authentication auth, Model model) {
        var items = notifications.list(auth.getName());
        model.addAttribute("items", items);
        // snapshot before marking: same entity instances get read=true below
        model.addAttribute("unreadIds", items.stream().filter(n -> !n.isRead()).map(n -> n.getId()).collect(java.util.stream.Collectors.toSet()));
        notifications.markAllRead(auth.getName());
        model.addAttribute("unreadCount", 0L);
        return "bildirisler";
    }
}

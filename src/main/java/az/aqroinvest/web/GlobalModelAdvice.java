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
    private final az.aqroinvest.repo.UserRepository users;
    private final org.springframework.context.ApplicationContext context;

    /** True only when DevTools runs (spring-boot:run): layout then loads livereload.js. */
    @ModelAttribute("liveReload")
    public boolean liveReload() {
        return context.containsBean("liveReloadServer");
    }

    /** Request path for the header's active nav link (Thymeleaf 3.1 no longer exposes #request). */
    @ModelAttribute("currentPath")
    public String currentPath(jakarta.servlet.http.HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Header shows the user's name instead of the e-mail (falls back to the part before @). */
    @ModelAttribute("userShortName")
    public String userShortName(Authentication auth) {
        if (auth == null) return null;
        return users.findByEmailIgnoreCase(auth.getName()).map(u -> u.getFullName()).filter(n -> n != null && !n.isBlank())
                .orElse(auth.getName().replaceAll("@.*", ""));
    }

    /** Same page in az / en / ru: current URL with ?lang= replaced (LocaleChangeInterceptor stores it in a cookie). */
    @ModelAttribute("langUrls")
    public java.util.Map<String, String> langUrls(jakarta.servlet.http.HttpServletRequest request) {
        String path = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (String l : I18n.LANGS)
            m.put(l, org.springframework.web.util.UriComponentsBuilder.fromUriString(path).replaceQueryParam("lang", l).build().toUriString());
        return m;
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(Authentication auth) {
        return auth == null ? 0 : notifications.countByUserEmailIgnoreCaseAndReadFalse(auth.getName());
    }
}

package az.aqroinvest.service;

import az.aqroinvest.domain.AuditLog;
import az.aqroinvest.repo.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AuditService {

    public static final ZoneId BAKU = ZoneId.of("Asia/Baku");

    private final AuditLogRepository repo;

    public void log(String action, String entity, Object entityId, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean known = auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName());
        log(known ? auth.getName() : "anonim",
                known ? auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "") : null,
                action, entity, entityId, details);
    }

    public void log(String actorEmail, String actorRole, String action, String entity, Object entityId, String details) {
        AuditLog a = new AuditLog();
        a.setActorEmail(actorEmail);
        a.setActorRole(actorRole);
        a.setAction(action);
        a.setEntity(entity);
        a.setEntityId(entityId == null ? null : String.valueOf(entityId));
        a.setDetails(details);
        a.setIp(currentIp());
        a.setAt(LocalDateTime.now(BAKU));
        repo.save(a);
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            return req.getRemoteAddr();
        }
        return null;
    }
}

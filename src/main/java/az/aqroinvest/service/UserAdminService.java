package az.aqroinvest.service;

import az.aqroinvest.domain.Role;
import az.aqroinvest.domain.User;
import az.aqroinvest.repo.UserRepository;
import az.aqroinvest.web.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Admin user management: staff accounts and activation. Investors only come from /qeydiyyat. */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    public static final Set<Role> STAFF_ROLES = Set.of(Role.OPERATOR, Role.COMMISSION, Role.ADMIN);
    /** No look-alike characters (0/O, 1/l/I) — the password is read off the screen once. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SessionRegistry sessions;
    private final AuditService audit;

    public List<User> all() {
        return users.findAll().stream()
                .sorted(Comparator.comparing((User u) -> u.getRole().ordinal()).reversed().thenComparing(User::getFullName))
                .toList();
    }

    /** Creates a staff user. @return the generated temporary password (shown once, never stored in plain text). */
    @Transactional
    public String createStaff(String fullName, String email, String phone, Role role, String actorEmail) {
        requireAdmin(actorEmail);
        if (role == null || !STAFF_ROLES.contains(role)) throw new BusinessException(I18n.t("staff.users.err.staffOnly"));
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) throw new BusinessException(I18n.t("staff.users.err.emailTaken"));
        String password = tempPassword();
        User u = new User();
        u.setFullName(fullName.trim());
        u.setEmail(normalized);
        u.setPhone(phone.trim());
        u.setRole(role);
        u.setPasswordHash(encoder.encode(password));
        u.setActive(true);
        users.save(u);
        audit.log("USER_CREATED", "User", normalized, role.name()); // never the password
        return password;
    }

    @Transactional
    public User setActive(Long id, boolean active, String actorEmail) {
        User actor = requireAdmin(actorEmail);
        User u = users.findById(id).orElseThrow(() -> new BusinessException(I18n.t("staff.users.err.notFound")));
        if (u.getId().equals(actor.getId()) && !active) throw new BusinessException(I18n.t("staff.users.err.self"));
        if (u.isActive() == active) return u;
        u.setActive(active);
        audit.log(active ? "USER_ENABLED" : "USER_DISABLED", "User", u.getEmail(), u.getRole().name());
        if (!active) expireSessions(u.getEmail());
        return u;
    }

    /** Logs the user out everywhere (their next request goes to /giris?deaktiv). */
    private void expireSessions(String email) {
        sessions.getAllPrincipals().stream()
                .filter(p -> p instanceof org.springframework.security.core.userdetails.UserDetails d && d.getUsername().equalsIgnoreCase(email))
                .forEach(p -> sessions.getAllSessions(p, false).forEach(org.springframework.security.core.session.SessionInformation::expireNow));
    }

    private User requireAdmin(String email) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        if (u.getRole() != Role.ADMIN) throw new BusinessException(I18n.t("staff.users.err.adminOnly"));
        return u;
    }

    static String tempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}

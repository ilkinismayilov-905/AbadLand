package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.CompetitionRepository;
import az.aqroinvest.repo.PlotRepository;
import az.aqroinvest.repo.UserRepository;
import az.aqroinvest.web.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static az.aqroinvest.service.AuditService.BAKU;

/** Competition approval (SPEC "təsdiq mexanizmi"): operator creates → commission approves/rejects. */
@Service
@RequiredArgsConstructor
public class CompetitionService {

    public record NewCompetition(String title, String requirements, Long minInvestment, LocalDate deadline, List<Long> plotIds) {}

    private final CompetitionRepository competitions;
    private final PlotRepository plots;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;

    /** Plots not in any PUBLISHED competition (selectable for a new one). */
    public List<Plot> freePlots() {
        Set<Long> taken = new HashSet<>();
        competitions.findByStatusOrderByDeadlineAsc(CompetitionStatus.PUBLISHED).forEach(c -> c.getPlots().forEach(p -> taken.add(p.getId())));
        return plots.findAll(Sort.by("code")).stream()
                .filter(p -> !taken.contains(p.getId()) && p.getStatus() == PlotStatus.AVAILABLE && p.isPublished()).toList();
    }

    /** Rule errors (form field → message) beyond bean validation. */
    public Map<String, String> check(NewCompetition n) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (n.deadline() != null && !n.deadline().isAfter(LocalDate.now(BAKU)))
            errors.put("deadline", I18n.t("staff.comp.deadlineFuture"));
        if (n.plotIds() == null || n.plotIds().isEmpty()) errors.put("plotIds", I18n.t("staff.comp.plotRequired"));
        else {
            Set<Long> free = new HashSet<>(freePlots().stream().map(Plot::getId).toList());
            if (!free.containsAll(n.plotIds())) errors.put("plotIds", I18n.t("staff.comp.plotTaken"));
        }
        return errors;
    }

    @Transactional
    public Competition create(NewCompetition n, String actorEmail) {
        User actor = actor(actorEmail, Role.OPERATOR);
        Map<String, String> errors = check(n);
        if (!errors.isEmpty()) throw new BusinessException(errors.values().iterator().next());
        Competition c = new Competition();
        c.setCode(nextCode(LocalDate.now(BAKU).getYear()));
        c.setTitle(n.title().trim());
        c.setRequirements(n.requirements().trim());
        c.setMinInvestment(n.minInvestment());
        c.setDeadline(n.deadline());
        c.setStatus(CompetitionStatus.PENDING_APPROVAL);
        c.setCreatedBy(actor);
        c.getPlots().addAll(plots.findAllById(n.plotIds()));
        competitions.save(c);
        audit.log("COMPETITION_CREATE", "Competition", c.getCode(), c.getTitle());
        notifications.notifyRole(Role.COMMISSION, I18n.stored("staff.notify.compPending", c.getCode(), c.getTitle()), "/idare/musabiqeler");
        return c;
    }

    @Transactional
    public void approve(Long id, String actorEmail) {
        User actor = actor(actorEmail, Role.COMMISSION);
        Competition c = pending(id);
        if (!c.getDeadline().isAfter(LocalDate.now(BAKU).minusDays(1)))
            throw new BusinessException(I18n.t("staff.comp.deadlinePassed"));
        c.setStatus(CompetitionStatus.PUBLISHED);
        c.setApprovedBy(actor);
        audit.log("COMPETITION_APPROVE", "Competition", c.getCode(), null);
        notifications.notify(c.getCreatedBy(), I18n.stored("staff.notify.compApproved", c.getCode()), "/idare/musabiqeler");
    }

    @Transactional
    public void reject(Long id, String actorEmail, String comment) {
        User actor = actor(actorEmail, Role.COMMISSION);
        Competition c = pending(id);
        if (comment == null || comment.isBlank()) throw new BusinessException(I18n.t("staff.comp.rejectReasonRequired"));
        c.setStatus(CompetitionStatus.REJECTED);
        c.setApprovedBy(actor);
        c.setComment(comment.trim());
        audit.log("COMPETITION_REJECT", "Competition", c.getCode(), comment.trim());
        notifications.notify(c.getCreatedBy(), I18n.stored("staff.notify.compRejected", c.getCode(), comment.trim()), "/idare/musabiqeler");
    }

    synchronized String nextCode(int year) {
        String prefix = "MUS-" + year + "-";
        String max = competitions.maxCodeWithPrefix(prefix);
        int next = (max == null ? 0 : Integer.parseInt(max.substring(prefix.length()))) + 1;
        return prefix + String.format("%02d", next);
    }

    private Competition pending(Long id) {
        Competition c = competitions.findById(id).orElseThrow(() -> new BusinessException(I18n.t("staff.comp.notFound")));
        if (c.getStatus() != CompetitionStatus.PENDING_APPROVAL) throw new BusinessException(I18n.t("staff.comp.notPending"));
        return c;
    }

    private User actor(String email, Role role) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        if (u.getRole() != role && u.getRole() != Role.ADMIN) throw new BusinessException(I18n.t("staff.comp.forbidden"));
        return u;
    }
}

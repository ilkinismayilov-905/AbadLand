package az.aqroinvest.service;

import az.aqroinvest.domain.*;
import az.aqroinvest.integration.EktisService;
import az.aqroinvest.repo.*;
import az.aqroinvest.web.I18n;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static az.aqroinvest.service.AuditService.BAKU;

/** Application state machine (SPEC). The ONLY place where Application.status changes. */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** Submission data after form validation. */
    public record NewApplication(Long plotId, String plannedCrop, Long investmentAzn, Integer jobs, Integer months,
                                 String businessPlan) {}

    private final ApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final PlotRepository plots;
    private final CropRepository crops;
    private final UserRepository users;
    private final FarmProjectRepository farms;
    private final PlotService plotService;
    private final StorageService storage;
    private final NotificationService notifications;
    private final AuditService audit;
    private final EktisService ektis;

    private final Map<Integer, Integer> lastIssued = new HashMap<>();

    // ---------------------------------------------------------------- queries

    /** Investor's own application; anything else → 404. */
    public Application getOwned(Long id, String email) {
        Application a = applications.findById(id).orElseThrow(ApplicationService::notFound);
        if (!a.getInvestor().getEmail().equalsIgnoreCase(email)) throw notFound();
        return a;
    }

    public Application get(Long id) {
        return applications.findById(id).orElseThrow(ApplicationService::notFound);
    }

    public List<StatusHistory> history(Application a) {
        return history.findByApplicationOrderByAtAscIdAsc(a);
    }

    /** Open competition for a plot, or null. */
    public Competition openCompetition(Plot plot) {
        return plotService.openCompetitionByPlot().get(plot.getId());
    }

    // ---------------------------------------------------------------- submit

    /** All SPEC rule violations at once, keyed by form field ("" = general). */
    public Map<String, String> checkSubmission(User investor, NewApplication n) {
        Map<String, String> errors = new LinkedHashMap<>();
        Plot plot = n.plotId() == null ? null : plots.findById(n.plotId()).orElse(null);
        Competition c = plot == null ? null : openCompetition(plot);
        if (c == null) errors.put("", I18n.t("apply.error.competitionClosed"));
        else if (n.investmentAzn() != null && n.investmentAzn() < c.getMinInvestment())
            errors.put("investmentAzn", I18n.t("apply.error.belowMinimum", money(c.getMinInvestment())));
        if (plot != null && n.plannedCrop() != null && crops.findByCode(n.plannedCrop())
                .filter(cr -> cr.getActivityType() == plot.getPurpose()).isEmpty())
            errors.put("plannedCrop", I18n.t("apply.error.cropNotSuitable"));
        if (investor.getInvestorType() == InvestorType.LEGAL ? isBlank(investor.getVoen()) : isBlank(investor.getFin()))
            errors.put("", I18n.t("apply.error.profileIncomplete",
                    I18n.t(investor.getInvestorType() == InvestorType.LEGAL ? "apply.cabinet.voen" : "apply.cabinet.fin")));
        return errors;
    }

    @Transactional
    public Application submit(String email, NewApplication n, MultipartFile businessPlanFile, MultipartFile otherFile) {
        User investor = users.findByEmailIgnoreCase(email).orElseThrow();
        if (investor.getRole() != Role.INVESTOR) throw new BusinessException(I18n.t("apply.error.investorOnly"));
        Map<String, String> errors = checkSubmission(investor, n);
        if (!errors.isEmpty()) throw new BusinessException(errors.values().iterator().next());
        if (businessPlanFile == null || businessPlanFile.isEmpty()) throw new BusinessException(I18n.t("apply.error.planFileRequired"));

        Plot plot = plots.findById(n.plotId()).orElseThrow();
        LocalDateTime now = LocalDateTime.now(BAKU);
        Application a = new Application();
        a.setNumber(nextNumber(now.getYear()));
        a.setInvestor(investor);
        a.setCompetition(openCompetition(plot));
        a.setPlot(plot);
        a.setPlannedCrop(n.plannedCrop());
        a.setInvestmentAzn(n.investmentAzn());
        a.setJobs(n.jobs());
        a.setMonths(n.months());
        a.setBusinessPlan(n.businessPlan().trim());
        a.setStatus(ApplicationStatus.SUBMITTED);
        a.setCreatedAt(now);
        a.setUpdatedAt(now);
        applications.save(a);
        storage.store(businessPlanFile, DocType.BUSINESS_PLAN, a);
        if (otherFile != null && !otherFile.isEmpty()) storage.store(otherFile, DocType.OTHER, a);

        record(a, null, ApplicationStatus.SUBMITTED, investor, null, I18n.stored("apply.notify.submitted", a.getNumber()));
        notifications.notifyRole(Role.OPERATOR, I18n.stored("apply.notify.newApplication", a.getNumber(), plot.getCode()), staffLink(a));
        return a;
    }

    /** QAR-<year>-<6 digits>, max existing + 1. synchronized + in-memory last value so parallel submits never collide. */
    synchronized String nextNumber(int year) {
        String prefix = "QAR-" + year + "-";
        String max = applications.maxNumberWithPrefix(prefix);
        int fromDb = max == null ? 0 : Integer.parseInt(max.substring(prefix.length()));
        int next = Math.max(fromDb, lastIssued.getOrDefault(year, 0)) + 1;
        lastIssued.put(year, next);
        return prefix + String.format("%06d", next);
    }

    // ---------------------------------------------------------------- transitions

    @Transactional
    public void startReview(Long id, String actorEmail) {
        User actor = actor(actorEmail, Role.OPERATOR);
        Application a = get(id);
        require(a, ApplicationStatus.SUBMITTED);
        change(a, ApplicationStatus.UNDER_REVIEW, actor, null, I18n.stored("apply.notify.reviewStarted", a.getNumber()));
    }

    @Transactional
    public void requestInfo(Long id, String actorEmail, String message) {
        User actor = actor(actorEmail, Role.OPERATOR);
        Application a = get(id);
        require(a, ApplicationStatus.UNDER_REVIEW);
        if (isBlank(message)) throw new BusinessException(I18n.t("apply.error.infoRequired"));
        a.setInfoRequest(message.trim());
        a.setInfoResponse(null);
        change(a, ApplicationStatus.INFO_REQUESTED, actor, message.trim(),
                I18n.stored("apply.notify.infoRequested", a.getNumber(), message.trim()));
    }

    @Transactional
    public void respond(Long id, String investorEmail, String response, MultipartFile file, DocType fileType) {
        Application a = getOwned(id, investorEmail);
        require(a, ApplicationStatus.INFO_REQUESTED);
        if (isBlank(response)) throw new BusinessException(I18n.t("apply.error.replyRequired"));
        String fileError = storage.check(file);
        if (fileError != null) throw new BusinessException(fileError);
        if (file != null && !file.isEmpty()) storage.store(file, fileType == null ? DocType.OTHER : fileType, a);
        a.setInfoResponse(response.trim());
        change(a, ApplicationStatus.UNDER_REVIEW, a.getInvestor(), response.trim(), I18n.stored("apply.notify.replySent", a.getNumber()));
        notifications.notifyRole(Role.OPERATOR, I18n.stored("apply.notify.investorReplied", a.getNumber()), staffLink(a));
    }

    @Transactional
    public void decide(Long id, String actorEmail, Decision decision, String reason) {
        User actor = actor(actorEmail, Role.COMMISSION);
        Application a = get(id);
        require(a, ApplicationStatus.UNDER_REVIEW);
        if (decision == null) throw new BusinessException(I18n.t("apply.error.decisionRequired"));
        if (reason == null || reason.trim().length() < 10) throw new BusinessException(I18n.t("apply.error.reasonTooShort"));
        a.setDecision(decision);
        a.setDecisionReason(reason.trim());
        change(a, ApplicationStatus.DECIDED, actor, reason.trim(), I18n.stored(decision == Decision.APPROVED
                ? "apply.notify.decidedApproved" : "apply.notify.decidedRejected", a.getNumber()));
        ektis.sendDecision(a);
        if (decision == Decision.APPROVED) createFarm(a);
    }

    private void createFarm(Application a) {
        FarmProject f = new FarmProject();
        f.setApplication(a);
        f.setPlot(a.getPlot());
        f.setInvestor(a.getInvestor());
        f.setStatus(FarmStatus.ACTIVE);
        f.setStartDate(LocalDate.now(BAKU));
        f.setCommittedInvestment(a.getInvestmentAzn());
        f.setCommittedJobs(a.getJobs());
        f.setCommittedMonths(a.getMonths());
        farms.save(f);
        audit.log("FARM_CREATE", "FarmProject", f.getId(), a.getNumber());
        notifications.notify(a.getInvestor(), I18n.stored("apply.notify.farmCreated", a.getPlot().getCode()), "/kabinet/teserrufat/" + f.getId());
    }

    // ---------------------------------------------------------------- helpers

    private void change(Application a, ApplicationStatus to, User actor, String comment, String message) {
        ApplicationStatus from = a.getStatus();
        a.setStatus(to);
        a.setUpdatedAt(LocalDateTime.now(BAKU));
        record(a, from, to, actor, comment, message);
    }

    /** StatusHistory + AuditLog + Notification to the investor — for every change. */
    private void record(Application a, ApplicationStatus from, ApplicationStatus to, User actor, String comment, String message) {
        StatusHistory h = new StatusHistory();
        h.setApplication(a);
        h.setFromStatus(from);
        h.setToStatus(to);
        h.setComment(comment);
        h.setActorName(actor.getFullName());
        h.setActorRole(actor.getRole());
        h.setAt(a.getUpdatedAt());
        history.save(h);
        audit.log(actor.getEmail(), actor.getRole().name(), from == null ? "APPLICATION_SUBMIT" : "STATUS_CHANGE",
                "Application", a.getNumber(), (from == null ? "" : from + " → ") + to);
        notifications.notify(a.getInvestor(), message, "/kabinet/muraciet/" + a.getId());
    }

    /** Actor must have the role (ADMIN may do everything). */
    private User actor(String email, Role role) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        if (u.getRole() != role && u.getRole() != Role.ADMIN) throw new BusinessException(I18n.t("apply.error.forbidden"));
        return u;
    }

    private static void require(Application a, ApplicationStatus expected) {
        if (a.getStatus() != expected) throw new BusinessException(I18n.t("apply.error.wrongStatus"));
    }

    private static String staffLink(Application a) {
        return "/idare/muracietler/" + a.getId();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String money(long v) {
        return String.format("%,d", v).replace(',', ' ') + " AZN";
    }
}

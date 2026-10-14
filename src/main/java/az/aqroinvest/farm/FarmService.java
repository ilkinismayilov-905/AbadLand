package az.aqroinvest.farm;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.AuditService;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.NotificationService;
import az.aqroinvest.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static az.aqroinvest.service.AuditService.BAKU;

/** Farm project after the lease (SPEC "Farm project", "Planting calendar"). Status and risk are recomputed on every read. */
@Service
@RequiredArgsConstructor
public class FarmService {

    /** Progress vs commitments, percentages 0..n (may exceed 100). */
    public record Progress(int investmentPct, int jobsPct, int timePct, int localPct, long investedToDate, int workers,
                           int localWorkers, int delayedTasks, ProgressReport lastReport, FarmStatus risk, List<String> riskReasons) {}

    /** Everything a farm page shows. */
    public record FarmView(FarmProject project, Progress progress, List<CropPlanItem> plan, double plannedArea,
                           Map<Long, List<CalendarTask>> tasksByItem, List<WorkforceItem> workforce,
                           List<ProgressReport> reports, Map<Long, List<Document>> photos) {
        public double freeArea() {
            return Math.max(0, Math.round((project.getPlot().getAreaHa() - plannedArea) * 10) / 10.0);
        }
    }

    public static final int MAX_PHOTOS = 3;

    private final FarmProjectRepository farms;
    private final CropPlanItemRepository plans;
    private final CalendarTaskRepository tasks;
    private final WorkforceItemRepository workforce;
    private final ProgressReportRepository reports;
    private final DocumentRepository documents;
    private final CropRepository crops;
    private final UserRepository users;
    private final CalendarService calendar;
    private final StorageService storage;
    private final NotificationService notifications;
    private final AuditService audit;
    private final az.aqroinvest.web.StatusLabels labels;

    // ---------------------------------------------------------------- reads

    public FarmProject getOwned(Long id, String email) {
        FarmProject f = farms.findById(id).orElseThrow(FarmService::notFound);
        if (!f.getInvestor().getEmail().equalsIgnoreCase(email)) throw notFound();
        return f;
    }

    public FarmProject get(Long id) {
        return farms.findById(id).orElseThrow(FarmService::notFound);
    }

    /** Refreshes task statuses + risk, then assembles the page data. */
    @Transactional
    public FarmView view(FarmProject f) {
        LocalDate today = LocalDate.now(BAKU);
        List<CalendarTask> all = refreshTasks(f, today);
        List<WorkforceItem> crew = workforce.findByProjectOrderByIdAsc(f);
        List<ProgressReport> reps = reports.findByProjectOrderBySubmittedAtDesc(f);
        Progress p = progress(f, crew, reps, all, today);
        if (f.getStatus() != FarmStatus.COMPLETED && f.getStatus() != p.risk()) f.setStatus(p.risk());
        List<CropPlanItem> plan = plans.findByProjectOrderByIdAsc(f);
        Map<Long, List<CalendarTask>> byItem = new LinkedHashMap<>();
        plan.forEach(i -> byItem.put(i.getId(), new ArrayList<>()));
        all.forEach(t -> byItem.computeIfAbsent(t.getCropPlanItem().getId(), k -> new ArrayList<>()).add(t));
        Map<Long, List<Document>> photos = new HashMap<>();
        reps.forEach(r -> photos.put(r.getId(), documents.findByReportOrderByIdAsc(r)));
        return new FarmView(f, p, plan, plan.stream().mapToDouble(CropPlanItem::getAreaHa).sum(), byItem, crew, reps, photos);
    }

    /** Monitoring list: every project with fresh progress. */
    @Transactional
    public List<FarmView> all() {
        return farms.findAllByOrderByIdDesc().stream().map(this::view).toList();
    }

    @Transactional
    public List<FarmProject> mine(String email) {
        List<FarmProject> list = farms.findByInvestorEmailIgnoreCaseOrderByIdDesc(email);
        list.forEach(this::view); // keeps status badges current
        return list;
    }

    /** Status computed on read; the first time a task turns DELAYED the investor gets one notification. */
    List<CalendarTask> refreshTasks(FarmProject f, LocalDate today) {
        List<CalendarTask> all = tasks.findByProjectOrderByPlannedStartAscIdAsc(f);
        for (CalendarTask t : all) {
            TaskStatus now = CalendarService.statusOf(t, today);
            if (now == TaskStatus.DELAYED && t.getStatus() != TaskStatus.DELAYED)
                notifications.notify(f.getInvestor(), "Əkin tapşırığı gecikir: " + t.getCropPlanItem().getCrop().getNameAz()
                        + " — " + labels.label(t.getStage()) + " (" + f.getPlot().getCode() + ")", "/kabinet/teserrufat/" + f.getId());
            t.setStatus(now);
        }
        return all;
    }

    /** SPEC progress + risk rule. Pure apart from its inputs (unit-tested). */
    static Progress progress(FarmProject f, List<WorkforceItem> crew, List<ProgressReport> reps, List<CalendarTask> all, LocalDate today) {
        ProgressReport last = reps.stream().max(Comparator.comparing(ProgressReport::getSubmittedAt)).orElse(null);
        long invested = last == null ? 0 : last.getInvestedToDate();
        int workers = crew.stream().mapToInt(WorkforceItem::getCount).sum();
        int local = crew.stream().filter(WorkforceItem::isLocal).mapToInt(WorkforceItem::getCount).sum();
        int invPct = pct(invested, f.getCommittedInvestment());
        int jobsPct = pct(workers, f.getCommittedJobs());
        double months = Math.max(0, ChronoUnit.DAYS.between(f.getStartDate(), today)) / 30.4375;
        int timePct = pct(months, f.getCommittedMonths());
        int localPct = workers == 0 ? 0 : pct(local, workers);
        int delayed = (int) all.stream().filter(t -> CalendarService.statusOf(t, today) == TaskStatus.DELAYED).count();

        List<String> reasons = new ArrayList<>();
        if (timePct >= 25 && invPct < timePct * 0.5) reasons.add("İnvestisiya vaxta görə geridə qalır (" + invPct + "% / vaxt " + timePct + "%)");
        if (timePct >= 25 && jobsPct < timePct * 0.5) reasons.add("İş yerləri vaxta görə geridə qalır (" + jobsPct + "% / vaxt " + timePct + "%)");
        LocalDate lastReportDate = last == null ? f.getStartDate() : last.getSubmittedAt().toLocalDate();
        if (ChronoUnit.DAYS.between(lastReportDate, today) > 100) reasons.add("100 gündən çoxdur hesabat yoxdur");
        boolean longDelay = all.stream().anyMatch(t -> t.getActualDate() == null && ChronoUnit.DAYS.between(t.getPlannedEnd(), today) > 30);
        if (longDelay) reasons.add("Əkin tapşırığı 30 gündən çox gecikir");
        return new Progress(invPct, jobsPct, timePct, localPct, invested, workers, local, delayed, last,
                reasons.isEmpty() ? FarmStatus.ACTIVE : FarmStatus.AT_RISK, reasons);
    }

    // ---------------------------------------------------------------- investor actions

    @Transactional
    public void addCropPlan(Long id, String email, String cropCode, Double areaHa, Integer seasonYear) {
        FarmProject f = getOwned(id, email);
        Crop crop = crops.findByCode(cropCode == null ? "" : cropCode).orElseThrow(() -> new BusinessException("Bitkini seçin"));
        if (areaHa == null || areaHa <= 0) throw new BusinessException("Sahə müsbət olmalıdır");
        int year = LocalDate.now(BAKU).getYear();
        if (seasonYear == null || seasonYear < year - 1 || seasonYear > year + 5)
            throw new BusinessException("Mövsüm ili " + (year - 1) + "–" + (year + 5) + " aralığında olmalıdır");
        double used = plans.findByProjectOrderByIdAsc(f).stream().mapToDouble(CropPlanItem::getAreaHa).sum();
        if (used + areaHa > f.getPlot().getAreaHa() + 1e-9)
            throw new BusinessException(String.format(Locale.ROOT, "Ümumi əkin sahəsi sahənin ölçüsünü (%.1f ha) aşa bilməz; boş qalıb: %.1f ha",
                    f.getPlot().getAreaHa(), f.getPlot().getAreaHa() - used).replace('.', ','));
        CropPlanItem i = new CropPlanItem();
        i.setProject(f);
        i.setCrop(crop);
        i.setAreaHa(areaHa);
        i.setSeasonYear(seasonYear);
        plans.save(i);
        int n = calendar.generate(i).size();
        audit.log("FARM_CROP_PLAN", "FarmProject", f.getId(), crop.getCode() + " " + areaHa + " ha " + seasonYear + " → " + n + " tasks");
    }

    @Transactional
    public void markDone(Long taskId, String email, LocalDate date, String note) {
        CalendarTask t = tasks.findById(taskId).orElseThrow(FarmService::notFound);
        getOwned(t.getProject().getId(), email);
        if (date == null) throw new BusinessException("İcra tarixini seçin");
        if (date.isAfter(LocalDate.now(BAKU))) throw new BusinessException("İcra tarixi gələcəkdə ola bilməz");
        t.setActualDate(date);
        t.setNote(note == null || note.isBlank() ? null : note.trim());
        t.setStatus(TaskStatus.DONE);
        audit.log("FARM_TASK_DONE", "CalendarTask", t.getId(), t.getStage() + " " + date);
    }

    @Transactional
    public void addWorkers(Long id, String email, WorkRole role, Integer count, boolean local) {
        FarmProject f = getOwned(id, email);
        if (role == null) throw new BusinessException("Vəzifəni seçin");
        if (count == null || count < 1 || count > 1000) throw new BusinessException("Say 1–1000 aralığında olmalıdır");
        WorkforceItem w = new WorkforceItem();
        w.setProject(f);
        w.setRole(role);
        w.setCount(count);
        w.setLocal(local);
        workforce.save(w);
        audit.log("FARM_WORKFORCE", "FarmProject", f.getId(), role + " ×" + count + (local ? " yerli" : ""));
    }

    @Transactional
    public void submitReport(Long id, String email, String period, Long investedToDate, String note, List<MultipartFile> photos) {
        FarmProject f = getOwned(id, email);
        if (period == null || !period.trim().matches("20\\d{2}-Q[1-4]")) throw new BusinessException("Dövrü 2026-Q4 formatında yazın");
        if (investedToDate == null || investedToDate < 0) throw new BusinessException("Faktiki investisiyanı yazın");
        if (note != null && note.length() > 2000) throw new BusinessException("Qeyd ən çox 2000 simvol ola bilər");
        List<MultipartFile> files = photos == null ? List.of() : photos.stream().filter(p -> p != null && !p.isEmpty()).toList();
        if (files.size() > MAX_PHOTOS) throw new BusinessException("Ən çox " + MAX_PHOTOS + " şəkil yükləyin");
        for (MultipartFile p : files) {
            String ext = p.getOriginalFilename() == null ? "" : p.getOriginalFilename().toLowerCase(Locale.ROOT);
            if (!(ext.endsWith(".jpg") || ext.endsWith(".jpeg") || ext.endsWith(".png"))) throw new BusinessException("Şəkillər yalnız JPG və ya PNG olmalıdır");
            String err = storage.check(p);
            if (err != null) throw new BusinessException(err);
        }
        ProgressReport r = new ProgressReport();
        r.setProject(f);
        r.setPeriod(period.trim());
        r.setInvestedToDate(investedToDate);
        r.setNote(note == null || note.isBlank() ? null : note.trim());
        r.setStatus(ReportStatus.SUBMITTED);
        r.setSubmittedAt(LocalDateTime.now(BAKU));
        reports.save(r);
        files.forEach(p -> storage.store(p, DocType.OTHER, null, r));
        audit.log("FARM_REPORT", "FarmProject", f.getId(), r.getPeriod() + " " + investedToDate + " AZN, " + files.size() + " photos");
        notifications.notifyRole(Role.OPERATOR, "Yeni rüblük hesabat: " + f.getPlot().getCode() + " (" + r.getPeriod() + ")",
                "/idare/monitorinq/" + f.getId());
    }

    // ---------------------------------------------------------------- staff actions

    @Transactional
    public void reviewReport(Long reportId, String actorEmail, boolean accept, String comment) {
        User actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow();
        if (actor.getRole() != Role.OPERATOR && actor.getRole() != Role.ADMIN) throw new BusinessException("Bu əməliyyat üçün icazəniz yoxdur");
        ProgressReport r = reports.findById(reportId).orElseThrow(() -> new BusinessException("Hesabat tapılmadı"));
        if (r.getStatus() != ReportStatus.SUBMITTED) throw new BusinessException("Hesabata artıq baxılıb");
        if (!accept && (comment == null || comment.isBlank())) throw new BusinessException("Nəyin düzəldilməli olduğunu yazın");
        r.setStatus(accept ? ReportStatus.ACCEPTED : ReportStatus.NEEDS_FIX);
        r.setReviewerComment(comment == null || comment.isBlank() ? null : comment.trim());
        FarmProject f = r.getProject();
        audit.log(actor.getEmail(), actor.getRole().name(), accept ? "FARM_REPORT_ACCEPT" : "FARM_REPORT_FIX", "ProgressReport", r.getId(), r.getReviewerComment());
        notifications.notify(f.getInvestor(), "Hesabat " + r.getPeriod() + (accept ? " qəbul edildi" : " üçün düzəliş tələb olunur: " + comment.trim()),
                "/kabinet/teserrufat/" + f.getId());
    }

    // ---------------------------------------------------------------- helpers

    static int pct(double value, double of) {
        return of <= 0 ? 0 : (int) Math.round(value * 100 / of);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /** Dashboard totals across all farms. */
    public record Totals(long activeProjects, long actualJobs, long promisedJobs, long delayedTasks) {}

    @Transactional
    public Totals totals() {
        List<FarmView> all = all();
        return new Totals(
                all.stream().filter(v -> v.project().getStatus() != FarmStatus.COMPLETED).count(),
                all.stream().mapToLong(v -> v.progress().workers()).sum(),
                all.stream().mapToLong(v -> v.project().getCommittedJobs()).sum(),
                all.stream().mapToLong(v -> v.progress().delayedTasks()).sum());
    }

    /** Distinct crops for the plan form (crops of the plot's activity). */
    public List<Crop> cropOptions(FarmProject f) {
        return crops.findAll().stream().filter(c -> c.getActivityType() == f.getPlot().getPurpose())
                .sorted(Comparator.comparing(Crop::getNameAz)).collect(Collectors.toList());
    }
}

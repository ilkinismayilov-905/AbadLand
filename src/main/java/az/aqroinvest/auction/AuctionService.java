package az.aqroinvest.auction;

import az.aqroinvest.domain.*;
import az.aqroinvest.integration.EktisService;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.AuditService;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static az.aqroinvest.service.AuditService.BAKU;

/** Open ascending auctions (SPEC "Open auction"). The ONLY place where Auction.status changes. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionService {

    public static final String LEGAL_NOTE = "Hərrac simulyasiyadır: zaminat və ödəniş real aparılmır. Dövlət torpaqlarının satışı və icarəsi "
            + "qanunvericiliyə və aidiyyəti qurumun qaydalarına uyğun aparılmalıdır; real tətbiqdə mövcud dövlət hərrac platforması ilə inteqrasiya nəzərdə tutulur.";
    static final Set<AuctionStatus> OPEN = EnumSet.of(AuctionStatus.PENDING_APPROVAL, AuctionStatus.SCHEDULED, AuctionStatus.LIVE, AuctionStatus.ENDED);
    static final Set<AuctionStatus> RUNNING = EnumSet.of(AuctionStatus.SCHEDULED, AuctionStatus.LIVE);

    public record NewAuction(Long plotId, DealType dealType, Long startPrice, Long step, Long deposit, Integer leaseYears,
                             LocalDateTime startsAt, LocalDateTime endsAt, String rules) {}

    /** Result of a bid for the UI. */
    public record BidResult(long amount, boolean extended, String message) {}

    private final AuctionRepository auctions;
    private final AuctionParticipantRepository participants;
    private final BidRepository bids;
    private final PlotRepository plots;
    private final CompetitionRepository competitions;
    private final UserRepository users;
    private final FarmProjectRepository farms;
    private final EktisService ektis;
    private final NotificationService notifications;
    private final AuditService audit;
    private final TaskScheduler scheduler;
    private final PlatformTransactionManager txManager;

    @Value("${app.auction.sale-enabled:true}")
    private boolean saleEnabled;
    @Value("${app.auction.demo-bot:false}")
    private boolean demoBot;

    private final Set<Long> botFired = ConcurrentHashMap.newKeySet();

    // ---------------------------------------------------------------- clock-driven status

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void scheduledTick() {
        new TransactionTemplate(txManager).executeWithoutResult(s -> tick(now()));
    }

    /** SCHEDULED → LIVE → ENDED / FAILED for every running auction whose time has come. Also called on every read. */
    @Transactional
    public void tick(LocalDateTime now) {
        for (Auction a : auctions.findByStatusIn(RUNNING))
            if (due(a, now)) advance(auctions.findByIdForUpdate(a.getId()).orElseThrow(), now);
    }

    private static boolean due(Auction a, LocalDateTime now) {
        return a.getStatus() == AuctionStatus.SCHEDULED && !now.isBefore(a.getStartsAt())
                || a.getStatus() == AuctionStatus.LIVE && !now.isBefore(a.getEndsAt());
    }

    private void advance(Auction a, LocalDateTime now) {
        if (a.getStatus() == AuctionStatus.SCHEDULED && !now.isBefore(a.getStartsAt())) {
            a.setStatus(AuctionStatus.LIVE);
            audit.log("system", null, "AUCTION_LIVE", "Auction", a.getCode(), null);
        }
        if (a.getStatus() == AuctionStatus.LIVE && !now.isBefore(a.getEndsAt())) {
            Optional<Bid> top = bids.findFirstByAuctionOrderByAmountDescIdDesc(a);
            if (top.isPresent()) {
                a.setStatus(AuctionStatus.ENDED);
                a.setWinner(top.get().getParticipant().getUser());
                a.setFinalPrice(top.get().getAmount());
                notifications.notify(a.getWinner(), "Hərrac " + a.getCode() + " başa çatdı — ən yüksək təklif sizindir ("
                        + money(a.getFinalPrice()) + "). Komissiyanın təsdiqi gözlənilir.", "/herraclar/" + a.getId());
                notifications.notifyRole(Role.COMMISSION, "Hərrac " + a.getCode() + " başa çatıb — nəticəni təsdiqləyin.", "/idare/herraclar");
            } else {
                a.setStatus(AuctionStatus.FAILED);
                a.getPlot().setStatus(PlotStatus.AVAILABLE);
            }
            audit.log("system", null, "AUCTION_" + a.getStatus(), "Auction", a.getCode(), a.getFinalPrice() == null ? null : String.valueOf(a.getFinalPrice()));
        }
    }

    /** Fresh status, then the auction (404 if missing). */
    @Transactional
    public Auction get(Long id) {
        tick(now());
        return auctions.findById(id).orElseThrow(() -> new BusinessException("Hərrac tapılmadı"));
    }

    @Transactional
    public List<Auction> list() {
        tick(now());
        return auctions.findAllByOrderByStartsAtDesc();
    }

    // ---------------------------------------------------------------- staff lifecycle

    @Transactional
    public Auction create(NewAuction n, String actorEmail) {
        User actor = actor(actorEmail, Role.OPERATOR);
        Plot plot = n.plotId() == null ? null : plots.findById(n.plotId()).orElse(null);
        if (plot == null) throw new BusinessException("Sahəni seçin");
        if (n.dealType() == null) throw new BusinessException("Əqd növünü seçin");
        if (n.dealType() == DealType.SALE && !saleEnabled) throw new BusinessException("Satış hərracları hazırda deaktivdir");
        if (n.startPrice() == null || n.startPrice() <= 0) throw new BusinessException("Başlanğıc qiymət müsbət olmalıdır");
        if (n.step() == null || n.step() <= 0) throw new BusinessException("Addım müsbət olmalıdır");
        if (n.deposit() == null || n.deposit() < 0) throw new BusinessException("Zaminət mənfi ola bilməz");
        if (n.dealType() == DealType.LEASE && (n.leaseYears() == null || n.leaseYears() < 1 || n.leaseYears() > 49))
            throw new BusinessException("İcarə müddəti 1–49 il olmalıdır");
        if (n.startsAt() == null || n.endsAt() == null || !n.startsAt().isBefore(n.endsAt())) throw new BusinessException("Başlama vaxtı bitmə vaxtından əvvəl olmalıdır");
        if (!n.endsAt().isAfter(now())) throw new BusinessException("Bitmə vaxtı gələcəkdə olmalıdır");
        checkPlotFree(plot);
        Auction a = new Auction();
        a.setCode(nextCode(now().getYear()));
        a.setPlot(plot);
        a.setDealType(n.dealType());
        a.setStartPrice(n.startPrice());
        a.setStep(n.step());
        a.setDeposit(n.deposit());
        a.setLeaseYears(n.dealType() == DealType.LEASE ? n.leaseYears() : null);
        a.setRules(n.rules() == null || n.rules().isBlank() ? defaultRules(n.dealType()) : n.rules().trim());
        a.setStartsAt(n.startsAt());
        a.setEndsAt(n.endsAt());
        a.setStatus(AuctionStatus.PENDING_APPROVAL);
        a.setCreatedBy(actor);
        a.setCreatedAt(now());
        auctions.save(a);
        audit.log("AUCTION_CREATE", "Auction", a.getCode(), plot.getCode() + " " + n.dealType());
        notifications.notifyRole(Role.COMMISSION, "Təsdiq gözləyən hərrac: " + a.getCode() + " (" + plot.getCode() + ")", "/idare/herraclar");
        return a;
    }

    void checkPlotFree(Plot plot) {
        boolean inCompetition = competitions.findByStatusOrderByDeadlineAsc(CompetitionStatus.PUBLISHED).stream()
                .anyMatch(c -> c.getPlots().stream().anyMatch(p -> p.getId().equals(plot.getId())));
        if (inCompetition) throw new BusinessException("Sahə dərc edilmiş müsabiqədədir");
        if (auctions.findByPlotOrderByStartsAtDesc(plot).stream().anyMatch(x -> OPEN.contains(x.getStatus())))
            throw new BusinessException("Bu sahə üçün artıq açıq hərrac var");
        if (plot.getStatus() != PlotStatus.AVAILABLE) throw new BusinessException("Sahə boş deyil");
    }

    @Transactional
    public void approve(Long id, String actorEmail) {
        User actor = actor(actorEmail, Role.COMMISSION);
        Auction a = locked(id);
        require(a, AuctionStatus.PENDING_APPROVAL);
        a.setStatus(AuctionStatus.SCHEDULED);
        a.setApprovedBy(actor);
        a.getPlot().setStatus(PlotStatus.IN_AUCTION);
        audit.log("AUCTION_APPROVE", "Auction", a.getCode(), null);
        notifications.notify(a.getCreatedBy(), "Hərrac təsdiqləndi: " + a.getCode(), "/idare/herraclar");
        advance(a, now());
    }

    @Transactional
    public void reject(Long id, String actorEmail, String comment) {
        actor(actorEmail, Role.COMMISSION);
        Auction a = locked(id);
        require(a, AuctionStatus.PENDING_APPROVAL);
        if (comment == null || comment.isBlank()) throw new BusinessException("Rədd etmənin səbəbini yazın");
        a.setStatus(AuctionStatus.REJECTED);
        a.setComment(comment.trim());
        audit.log("AUCTION_REJECT", "Auction", a.getCode(), comment.trim());
        notifications.notify(a.getCreatedBy(), "Hərrac rədd edildi: " + a.getCode() + " — " + comment.trim(), "/idare/herraclar");
    }

    @Transactional
    public void cancel(Long id, String actorEmail, String comment) {
        actor(actorEmail, Role.OPERATOR);
        Auction a = locked(id);
        require(a, AuctionStatus.SCHEDULED);
        if (comment == null || comment.isBlank()) throw new BusinessException("Ləğv etmənin səbəbini yazın");
        a.setStatus(AuctionStatus.CANCELLED);
        a.setComment(comment.trim());
        a.getPlot().setStatus(PlotStatus.AVAILABLE);
        audit.log("AUCTION_CANCEL", "Auction", a.getCode(), comment.trim());
    }

    /** Commission signs the protocol: winner notified, EKTİS, plot leased/sold, LEASE → FarmProject. */
    @Transactional
    public void confirm(Long id, String actorEmail) {
        User actor = actor(actorEmail, Role.COMMISSION);
        tick(now());
        Auction a = locked(id);
        require(a, AuctionStatus.ENDED);
        a.setStatus(AuctionStatus.CONFIRMED);
        a.setConfirmedBy(actor);
        a.setConfirmedAt(now());
        a.getPlot().setStatus(a.getDealType() == DealType.LEASE ? PlotStatus.LEASED : PlotStatus.SOLD);
        audit.log("AUCTION_CONFIRM", "Auction", a.getCode(), a.getFinalPrice() + " AZN");
        ektis.sendAuctionResult(a, bids.countByAuction(a));
        String link = "/herraclar/" + a.getId() + "/protokol";
        if (a.getDealType() == DealType.LEASE) {
            FarmProject f = new FarmProject();
            f.setAuction(a);
            f.setPlot(a.getPlot());
            f.setInvestor(a.getWinner());
            f.setStatus(FarmStatus.ACTIVE);
            f.setStartDate(now().toLocalDate());
            f.setCommittedInvestment(0);
            f.setCommittedJobs(0);
            f.setCommittedMonths(a.getLeaseYears() * 12);
            farms.save(f);
            audit.log("FARM_CREATE", "FarmProject", f.getId(), a.getCode());
            link = "/kabinet/teserrufat/" + f.getId();
        }
        notifications.notify(a.getWinner(), "Hərrac " + a.getCode() + " nəticəsi təsdiqləndi: siz qalibsiniz (" + money(a.getFinalPrice()) + ")"
                + (a.getDealType() == DealType.LEASE ? ". Təsərrüfat layihəniz yaradıldı." : "."), link);
    }

    // ---------------------------------------------------------------- participation

    @Transactional
    public AuctionParticipant register(Long id, String email, boolean rulesAccepted) {
        User u = investor(email);
        Auction a = lockedFresh(id);
        if (!RUNNING.contains(a.getStatus()) || !now().isBefore(a.getEndsAt())) throw new BusinessException("Bu hərraca qeydiyyat bağlıdır");
        Optional<AuctionParticipant> existing = participants.findByAuctionAndUserEmailIgnoreCase(a, email);
        if (existing.isPresent()) return existing.get();
        if (!rulesAccepted) throw new BusinessException("Hərrac qaydalarını qəbul edin");
        AuctionParticipant p = new AuctionParticipant();
        p.setAuction(a);
        p.setUser(u);
        p.setNumber((int) participants.countByAuction(a) + 1);
        p.setRegisteredAt(now());
        participants.save(p);
        audit.log("AUCTION_REGISTER", "Auction", a.getCode(), "#" + p.getNumber());
        return p;
    }

    /** Simulated deposit ("Zaminat ödənildi"). */
    @Transactional
    public void confirmDeposit(Long id, String email) {
        Auction a = get(id);
        if (!RUNNING.contains(a.getStatus()) || !now().isBefore(a.getEndsAt())) throw new BusinessException("Bu hərrac üçün zaminat artıq qəbul edilmir");
        AuctionParticipant p = participants.findByAuctionAndUserEmailIgnoreCase(a, email)
                .orElseThrow(() -> new BusinessException("Əvvəlcə hərraca qeydiyyatdan keçin"));
        if (p.isDepositConfirmed()) return;
        p.setDepositConfirmed(true);
        audit.log("AUCTION_DEPOSIT", "Auction", a.getCode(), "#" + p.getNumber() + " " + a.getDeposit() + " AZN (simulyasiya)");
    }

    @Transactional
    public BidResult placeBid(Long id, String email, Long amount) {
        return placeBid(id, email, amount, now(), false);
    }

    /** SPEC bidding rules 1–6 under a row lock. now is a parameter for tests. */
    @Transactional
    public BidResult placeBid(Long id, String email, Long amount, LocalDateTime now, boolean bot) {
        investor(email);
        tick(now);
        Auction a = locked(id);
        if (a.getStatus() != AuctionStatus.LIVE || !now.isBefore(a.getEndsAt())) throw new BusinessException("Hərrac hazırda getmir — təklif qəbul edilmir");
        AuctionParticipant me = participants.findByAuctionAndUserEmailIgnoreCase(a, email)
                .orElseThrow(() -> new BusinessException("Təklif vermək üçün hərraca qeydiyyatdan keçin"));
        if (!me.isDepositConfirmed()) throw new BusinessException("Təklif vermək üçün zaminatı təsdiqləyin");
        if (amount == null || amount < a.minNextBid())
            throw new BusinessException("Təklif ən azı " + money(a.minNextBid()) + " olmalıdır");
        Optional<Bid> top = bids.findFirstByAuctionOrderByAmountDescIdDesc(a);
        if (top.isPresent() && top.get().getParticipant().getId().equals(me.getId())) throw new BusinessException("Ən yüksək təklif artıq sizindir");

        boolean extended = Duration.between(now, a.getEndsAt()).getSeconds() < a.getExtendSeconds();
        if (extended) {
            a.setEndsAt(now.plusSeconds(a.getExtendSeconds()));
            a.setExtensions(a.getExtensions() + 1);
        }
        Bid b = new Bid();
        b.setAuction(a);
        b.setParticipant(me);
        b.setAmount(amount);
        b.setCreatedAt(now);
        b.setExtended(extended);
        bids.save(b);
        a.setCurrentPrice(amount);
        audit.log(email, Role.INVESTOR.name(), bot ? "AUCTION_BID_DEMO_BOT" : "AUCTION_BID", "Auction", a.getCode(),
                "#" + me.getNumber() + " " + amount + (extended ? " (+" + a.getExtendSeconds() + " s)" : ""));
        top.ifPresent(prev -> notifications.notify(prev.getParticipant().getUser(), "Təklifiniz keçildi: " + a.getCode()
                + " — yeni qiymət " + money(amount), "/herraclar/" + a.getId()));
        if (demoBot && !bot && me.getNumber() != 1 && "HRC-2026-01".equals(a.getCode()) && botFired.add(a.getId()))
            scheduler.schedule(() -> botBid(a.getId()), Instant.now().plusSeconds(8));
        return new BidResult(amount, extended, extended ? "Təklif qəbul edildi. Vaxt " + (a.getExtendSeconds() / 60) + " dəqiqə uzadıldı." : "Təklif qəbul edildi.");
    }

    /** Demo only: participant #1 outbids once (makes the live demo feel real). */
    void botBid(Long id) {
        try {
            new TransactionTemplate(txManager).executeWithoutResult(s -> {
                Auction a = auctions.findById(id).orElseThrow();
                participants.findAll().stream().filter(p -> p.getAuction().getId().equals(id) && p.getNumber() == 1).findFirst()
                        .ifPresent(p -> placeBid(id, p.getUser().getEmail(), a.minNextBid(), now(), true));
            });
        } catch (BusinessException e) {
            log.info("Demo bot skipped: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    public List<Bid> bidsOf(Auction a) {
        return bids.findByAuctionOrderByIdDesc(a);
    }

    public long participantCount(Auction a) {
        return participants.countByAuction(a);
    }

    public Optional<AuctionParticipant> participantOf(Auction a, String email) {
        return email == null ? Optional.empty() : participants.findByAuctionAndUserEmailIgnoreCase(a, email);
    }

    public List<Auction> forPlot(Plot p) {
        return auctions.findByPlotOrderByStartsAtDesc(p);
    }

    public List<AuctionParticipant> myParticipations(String email) {
        tick(now());
        return participants.findByUserEmailIgnoreCaseOrderByIdDesc(email);
    }

    public Optional<Bid> lastBidOf(AuctionParticipant p) {
        return bids.findFirstByParticipantOrderByIdDesc(p);
    }

    public boolean saleEnabled() {
        return saleEnabled;
    }

    /** Dashboard: running auctions and mean uplift of confirmed ones. */
    @Transactional
    public long[] kpis() {
        tick(now());
        long active = auctions.countByStatusIn(RUNNING);
        double avg = auctions.findByStatus(AuctionStatus.CONFIRMED).stream().map(Auction::upliftPct).filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue).average().orElse(0);
        return new long[]{active, Math.round(avg)};
    }

    synchronized String nextCode(int year) {
        String prefix = "HRC-" + year + "-";
        String max = auctions.maxCodeWithPrefix(prefix);
        int next = (max == null ? 0 : Integer.parseInt(max.substring(prefix.length()))) + 1;
        return prefix + String.format("%02d", next);
    }

    public static String defaultRules(DealType t) {
        return (t == DealType.LEASE ? "Təklif — bütün sahə üçün illik icarə haqqıdır (AZN/il). " : "Təklif — sahənin ümumi satış qiymətidir (AZN). ")
                + "Hər təklif cari qiymətdən ən azı bir addım yüksək olmalıdır. Son 2 dəqiqədə verilən təklif bitmə vaxtını 2 dəqiqə uzadır. "
                + "Təklif vermək üçün qeydiyyat və zaminat tələb olunur. Qalib — vaxt bitəndə ən yüksək təklif verən iştirakçıdır; nəticəni komissiya təsdiqləyir.";
    }

    private Auction locked(Long id) {
        return auctions.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("Hərrac tapılmadı"));
    }

    private Auction lockedFresh(Long id) {
        tick(now());
        return locked(id);
    }

    private static void require(Auction a, AuctionStatus s) {
        if (a.getStatus() != s) throw new BusinessException("Bu əməliyyat hərracın hazırkı statusunda mümkün deyil");
    }

    private User actor(String email, Role role) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        if (u.getRole() != role && u.getRole() != Role.ADMIN) throw new BusinessException("Bu əməliyyat üçün icazəniz yoxdur");
        return u;
    }

    /** Only investors with FİN (individual) or VÖEN (legal) take part; staff never bid. */
    private User investor(String email) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow();
        if (u.getRole() != Role.INVESTOR) throw new BusinessException("Hərracda yalnız investorlar iştirak edə bilər");
        boolean verified = u.getInvestorType() == InvestorType.LEGAL ? notBlank(u.getVoen()) : notBlank(u.getFin());
        if (!verified) throw new BusinessException("Profilinizdə FİN/VÖEN qeyd olunmayıb — əvvəlcə kabinetdə profili tamamlayın");
        return u;
    }

    static LocalDateTime now() {
        return LocalDateTime.now(BAKU);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String money(long v) {
        return String.format("%,d", v).replace(',', ' ') + " AZN";
    }
}

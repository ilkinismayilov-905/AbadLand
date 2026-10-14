package az.aqroinvest.integration;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.ApplicationRepository;
import az.aqroinvest.repo.IntegrationLogRepository;
import az.aqroinvest.repo.UserRepository;
import az.aqroinvest.service.AuditService;
import az.aqroinvest.service.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static az.aqroinvest.service.AuditService.BAKU;

/** Sends commission decisions to EKTİS (SPEC payload schemaVersion 1.0) and logs every call. */
@Service
@RequiredArgsConstructor
public class EktisService {

    /** Sender id in every EKTİS payload. */
    static final String SOURCE = "AGROINVEST_QARABAG";

    private final EktisClient client;
    private final IntegrationLogRepository logs;
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final AuditService audit;
    private final ObjectMapper json;

    /** Called by ApplicationService.decide. A failure is logged (FAILED) and can be retried; the decision itself stands. */
    @Transactional
    public IntegrationLog sendDecision(Application app) {
        IntegrationLog l = send(app.getNumber(), "SEND_DECISION", payload(app));
        if (l.getStatus() == IntegrationStatus.SUCCESS) app.setEktisRef(l.getEktisRef());
        audit.log("EKTIS_SEND", "Application", app.getNumber(), l.getStatus() + (l.getEktisRef() != null ? " " + l.getEktisRef() : ""));
        return l;
    }

    /** Confirmed auction result (operation SEND_AUCTION_RESULT); the log's number column holds the auction code. */
    @Transactional
    public IntegrationLog sendAuctionResult(Auction a, long bids) {
        ObjectNode n = json.createObjectNode();
        n.put("schemaVersion", "1.0");
        n.put("source", SOURCE);
        n.put("operation", "SEND_AUCTION_RESULT");
        n.put("auctionCode", a.getCode());
        n.put("dealType", a.getDealType().name());
        n.put("startPrice", a.getStartPrice());
        n.put("finalPrice", a.getFinalPrice());
        n.put("bids", bids);
        if (a.getLeaseYears() != null) n.put("leaseYears", a.getLeaseYears());
        ObjectNode plot = n.putObject("plot");
        plot.put("code", a.getPlot().getCode());
        plot.put("district", a.getPlot().getDistrict());
        plot.put("areaHa", a.getPlot().getAreaHa());
        ObjectNode w = n.putObject("winner");
        User u = a.getWinner();
        w.put("type", u.getInvestorType() == null ? null : u.getInvestorType().name());
        if (u.getInvestorType() == InvestorType.LEGAL) w.put("voen", u.getVoen());
        else w.put("fin", u.getFin());
        IntegrationLog l = send(a.getCode(), "SEND_AUCTION_RESULT", n.toString());
        audit.log("EKTIS_SEND", "Auction", a.getCode(), l.getStatus() + (l.getEktisRef() != null ? " " + l.getEktisRef() : ""));
        return l;
    }

    private IntegrationLog send(String number, String operation, String payload) {
        long start = System.nanoTime();
        EktisResult r = client.send(payload);
        IntegrationLog l = new IntegrationLog();
        l.setApplicationNumber(number);
        l.setOperation(operation);
        l.setRequestJson(payload);
        l.setResponseJson(r.responseJson());
        l.setStatus(r.success() ? IntegrationStatus.SUCCESS : IntegrationStatus.FAILED);
        l.setEktisRef(r.reference());
        l.setDurationMs((System.nanoTime() - start) / 1_000_000);
        l.setAt(LocalDateTime.now(BAKU));
        logs.save(l);
        return l;
    }

    /** Admin re-sends a FAILED call (only if the application has no EKTİS reference yet). */
    @Transactional
    public IntegrationLog retry(Long logId, String actorEmail) {
        User actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow();
        if (actor.getRole() != Role.ADMIN) throw new BusinessException("Yenidən göndərmə yalnız admin üçündür");
        IntegrationLog failed = logs.findById(logId).orElseThrow(() -> new BusinessException("Jurnal tapılmadı"));
        if (failed.getStatus() != IntegrationStatus.FAILED) throw new BusinessException("Yalnız uğursuz göndərişlər təkrarlana bilər");
        if (!"SEND_DECISION".equals(failed.getOperation())) throw new BusinessException("Bu növ göndəriş üçün təkrar hələ mövcud deyil");
        Application app = applications.findByNumber(failed.getApplicationNumber())
                .orElseThrow(() -> new BusinessException("Müraciət tapılmadı"));
        if (app.getEktisRef() != null) throw new BusinessException("Bu qərar artıq EKTİS-ə çatdırılıb: " + app.getEktisRef());
        return sendDecision(app);
    }

    public String mode() {
        return client.mode();
    }

    String payload(Application a) {
        ObjectNode n = json.createObjectNode();
        n.put("schemaVersion", "1.0");
        n.put("source", SOURCE);
        n.put("applicationNumber", a.getNumber());
        n.put("decision", a.getDecision() == null ? null : a.getDecision().name());
        n.put("reason", a.getDecisionReason());
        n.put("investmentAzn", a.getInvestmentAzn());
        n.put("jobs", a.getJobs());
        ObjectNode inv = n.putObject("investor");
        User u = a.getInvestor();
        inv.put("type", u.getInvestorType() == null ? null : u.getInvestorType().name());
        if (u.getInvestorType() == InvestorType.LEGAL) inv.put("voen", u.getVoen());
        else inv.put("fin", u.getFin());
        ObjectNode plot = n.putObject("plot");
        plot.put("code", a.getPlot().getCode());
        plot.put("district", a.getPlot().getDistrict());
        plot.put("areaHa", a.getPlot().getAreaHa());
        plot.put("purpose", a.getPlot().getPurpose().name());
        n.put("competitionCode", a.getCompetition().getCode());
        return n.toString();
    }
}

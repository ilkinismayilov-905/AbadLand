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

    private final EktisClient client;
    private final IntegrationLogRepository logs;
    private final ApplicationRepository applications;
    private final UserRepository users;
    private final AuditService audit;
    private final ObjectMapper json;

    /** Called by ApplicationService.decide. A failure is logged (FAILED) and can be retried; the decision itself stands. */
    @Transactional
    public IntegrationLog sendDecision(Application app) {
        String payload = payload(app);
        long start = System.nanoTime();
        EktisResult r = client.send(payload);
        IntegrationLog l = new IntegrationLog();
        l.setApplicationNumber(app.getNumber());
        l.setOperation("SEND_DECISION");
        l.setRequestJson(payload);
        l.setResponseJson(r.responseJson());
        l.setStatus(r.success() ? IntegrationStatus.SUCCESS : IntegrationStatus.FAILED);
        l.setEktisRef(r.reference());
        l.setDurationMs((System.nanoTime() - start) / 1_000_000);
        l.setAt(LocalDateTime.now(BAKU));
        logs.save(l);
        if (r.success()) app.setEktisRef(r.reference());
        audit.log("EKTIS_SEND", "Application", app.getNumber(), l.getStatus() + (r.success() ? " " + r.reference() : ""));
        return l;
    }

    /** Admin re-sends a FAILED call (only if the application has no EKTİS reference yet). */
    @Transactional
    public IntegrationLog retry(Long logId, String actorEmail) {
        User actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow();
        if (actor.getRole() != Role.ADMIN) throw new BusinessException("Yenidən göndərmə yalnız admin üçündür");
        IntegrationLog failed = logs.findById(logId).orElseThrow(() -> new BusinessException("Jurnal tapılmadı"));
        if (failed.getStatus() != IntegrationStatus.FAILED) throw new BusinessException("Yalnız uğursuz göndərişlər təkrarlana bilər");
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

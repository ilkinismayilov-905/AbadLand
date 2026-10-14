package az.aqroinvest.web;

import az.aqroinvest.integration.EktisService;
import az.aqroinvest.integration.MockEktisClient;
import az.aqroinvest.repo.AuditLogRepository;
import az.aqroinvest.repo.IntegrationLogRepository;
import az.aqroinvest.service.AuditService;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.DashboardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Iterator;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboard;
    private final AuditLogRepository auditLogs;
    private final IntegrationLogRepository integrationLogs;
    private final EktisService ektis;
    private final ObjectProvider<MockEktisClient> mockClient;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Fmt fmt;
    private final az.aqroinvest.ai.ClimateService climate;
    private final az.aqroinvest.farm.FarmService farmService;
    private final az.aqroinvest.auction.AuctionService auctionService;

    @GetMapping("/idare")
    public String dashboard(Model model) {
        model.addAttribute("d", dashboard.build());
        model.addAttribute("farmTotals", farmService.totals());
        model.addAttribute("auctionKpis", auctionService.kpis());
        return "idare";
    }

    /** Admin: refresh climate for all plots from Open-Meteo (then RULE suitability is recomputed). */
    @PostMapping("/idare/iqlim-yenile")
    public String refreshClimate(Authentication auth, RedirectAttributes ra) {
        if (auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            ra.addFlashAttribute("error", "Bu əməliyyat yalnız admin üçündür.");
            return "redirect:/idare";
        }
        int n = climate.refreshAll();
        audit.log("CLIMATE_REFRESH", "Plot", null, n + " plots");
        if (n > 0) ra.addFlashAttribute("success", "İqlim məlumatı yeniləndi: " + n + " sahə (Open-Meteo).");
        else ra.addFlashAttribute("error", "Open-Meteo əlçatan deyil — mövcud dəyərlər saxlanıldı.");
        return "redirect:/idare";
    }

    @GetMapping("/idare/audit")
    public String audit(Model model) {
        model.addAttribute("entries", auditLogs.findTop200ByOrderByAtDescIdDesc());
        return "idare-audit";
    }

    @GetMapping("/idare/inteqrasiya")
    public String integration(Model model) {
        var logs = integrationLogs.findAllByOrderByAtDescIdDesc();
        model.addAttribute("logs", logs);
        model.addAttribute("pretty", logs.stream().collect(java.util.stream.Collectors.toMap(
                l -> l.getId(), l -> new String[]{prettyMasked(l.getRequestJson()), prettyMasked(l.getResponseJson())})));
        model.addAttribute("mode", ektis.mode());
        MockEktisClient mock = mockClient.getIfAvailable();
        model.addAttribute("mockFail", mock != null && mock.isFail());
        model.addAttribute("isMock", mock != null);
        return "idare-inteqrasiya";
    }

    @PostMapping("/idare/inteqrasiya/{id}/yeniden")
    public String retry(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            var l = ektis.retry(id, auth.getName());
            if (l.getStatus().name().equals("SUCCESS")) ra.addFlashAttribute("success", "Yenidən göndərildi: " + l.getEktisRef());
            else ra.addFlashAttribute("error", "EKTİS yenə cavab vermədi — sonra təkrar edin.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/inteqrasiya";
    }

    /** Admin: switch the mock between success and failure (demo of FAILED → retry) without a restart. */
    @PostMapping("/idare/inteqrasiya/xeta-rejimi")
    public String toggleFail(Authentication auth, RedirectAttributes ra) {
        MockEktisClient mock = mockClient.getIfAvailable();
        if (mock == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            ra.addFlashAttribute("error", "Bu əməliyyat yalnız admin üçün və MOCK rejimində mümkündür.");
            return "redirect:/idare/inteqrasiya";
        }
        mock.setFail(!mock.isFail());
        audit.log("EKTIS_MOCK_FAIL", "Integration", null, String.valueOf(mock.isFail()));
        ra.addFlashAttribute("success", mock.isFail() ? "Mock xəta rejimi AKTİV: göndərişlər uğursuz olacaq." : "Mock normal rejimdədir.");
        return "redirect:/idare/inteqrasiya";
    }

    /** Pretty JSON for the UI with FİN/VÖEN masked. */
    String prettyMasked(String raw) {
        if (raw == null) return "";
        try {
            JsonNode n = json.readTree(raw);
            mask(n);
            return json.writerWithDefaultPrettyPrinter().writeValueAsString(n);
        } catch (Exception e) {
            return raw;
        }
    }

    private void mask(JsonNode n) {
        if (n instanceof ObjectNode o) {
            for (Iterator<Map.Entry<String, JsonNode>> it = o.fields(); it.hasNext(); ) {
                var e = it.next();
                if ((e.getKey().equals("fin") || e.getKey().equals("voen")) && e.getValue().isTextual())
                    o.put(e.getKey(), fmt.mask(e.getValue().asText()));
                else mask(e.getValue());
            }
        } else if (n != null && n.isArray()) n.forEach(this::mask);
    }
}

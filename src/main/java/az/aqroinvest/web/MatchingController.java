package az.aqroinvest.web;

import az.aqroinvest.domain.ActivityType;
import az.aqroinvest.service.MatchingService;
import az.aqroinvest.service.MatchingService.Answers;
import az.aqroinvest.service.PlotService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashMap;
import java.util.Map;

import static az.aqroinvest.service.MatchingService.BUDGET_CUSTOM;
import static az.aqroinvest.service.MatchingService.BUDGET_NO_LIMIT;
import static az.aqroinvest.service.MatchingService.CUSTOM_SIZE;

/** Quiz and results on one GET URL so results are shareable. */
@Controller
@RequiredArgsConstructor
public class MatchingController {

    static final Map<Long, String> BUDGETS = new LinkedHashMap<>();
    static final Map<String, String> SIZES = new LinkedHashMap<>();
    static final Map<String, String> EXPERIENCES = new LinkedHashMap<>();

    static {
        BUDGETS.put(100_000L, "100\u00a0000 AZN");
        BUDGETS.put(250_000L, "250\u00a0000 AZN");
        BUDGETS.put(500_000L, "500\u00a0000 AZN");
        BUDGETS.put(1_000_000L, "1\u00a0000\u00a0000 AZN");
        BUDGETS.put(BUDGET_NO_LIMIT, "2\u00a0000\u00a0000+ AZN");
        BUDGETS.put(BUDGET_CUSTOM, "Digər məbləğ");
        for (String s : new String[]{"1-5", "5-20", "20-50", "50-150"}) SIZES.put(s, s.replace('-', '–') + " ha");
        SIZES.put("150+", "150+ ha");
        SIZES.put(CUSTOM_SIZE, "Özüm yazım");
        for (String e : new String[]{"0", "1-3", "3-10", "10+"}) EXPERIENCES.put(e, e.replace('-', '–') + " il");
    }

    private final MatchingService matching;
    private final PlotService plots;
    private final StatusLabels labels;
    private final Fmt fmt;

    @GetMapping("/uygunluq")
    public String quiz(@ModelAttribute("a") Answers a, @RequestParam(required = false) String edit,
                       HttpServletRequest request, Model model) {
        model.addAttribute("activities", ActivityType.values());
        model.addAttribute("budgets", BUDGETS);
        model.addAttribute("sizes", SIZES);
        model.addAttribute("districts", plots.districts());
        model.addAttribute("experiences", EXPERIENCES);
        boolean submitted = a.activity() != null || a.budget() != null || a.size() != null || a.experience() != null;
        String problem = !submitted ? null : !a.complete() ? "Zəhmət olmasa bütün 5 suala cavab verin." : a.invalid();
        if (problem != null) model.addAttribute("error", problem);
        if (submitted && problem == null && edit == null) {
            try {
                model.addAttribute("matches", matching.match(a));
                model.addAttribute("summary", summary(a));
                var editLink = org.springframework.web.util.UriComponentsBuilder.fromPath("/uygunluq");
                request.getParameterMap().forEach((k, v) -> editLink.queryParam(k, (Object[]) v));
                model.addAttribute("editUrl", editLink.replaceQueryParam("edit", 1).encode().build().toUriString());
            } catch (IllegalArgumentException e) {
                model.addAttribute("error", "Cavablar düzgün deyil. Testi yenidən keçin.");
            }
        }
        return "uygunluq";
    }

    /** "Seçiminiz: Digər — arıçılıq · 120 000 AZN · 10–30 ha · Füzuli · təcrübə 3–10 il" */
    String summary(Answers a) {
        String activity = labels.label(a.activity())
                + (a.activity() == ActivityType.OTHER ? " — " + a.otherText().strip() : "");
        String budget = a.budget() == BUDGET_CUSTOM ? fmt.money(a.effectiveBudget()) : BUDGETS.get(a.budget());
        String size = CUSTOM_SIZE.equals(a.size()) ? a.minHa().strip() + "–" + a.maxHa().strip() + " ha" : SIZES.get(a.size());
        String district = a.district().isEmpty() ? "Rayon fərqi yoxdur" : a.district();
        return "Seçiminiz: " + activity + " · " + budget + " · " + size + " · " + district
                + " · təcrübə " + EXPERIENCES.getOrDefault(a.experience(), a.experience());
    }
}

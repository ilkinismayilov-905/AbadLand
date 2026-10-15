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

    /** Option labels, built per request (language). */
    static Map<Long, String> budgets() {
        Map<Long, String> m = new LinkedHashMap<>();
        m.put(100_000L, "100\u00a0000 AZN");
        m.put(250_000L, "250\u00a0000 AZN");
        m.put(500_000L, "500\u00a0000 AZN");
        m.put(1_000_000L, "1\u00a0000\u00a0000 AZN");
        m.put(BUDGET_NO_LIMIT, "2\u00a0000\u00a0000+ AZN");
        m.put(BUDGET_CUSTOM, I18n.t("plots.quiz.budgetOther"));
        return m;
    }

    static Map<String, String> sizes() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String s : new String[]{"1-5", "5-20", "20-50", "50-150", "150+"}) m.put(s, I18n.t("plots.ha", s.replace('-', '–')));
        m.put(CUSTOM_SIZE, I18n.t("plots.quiz.sizeCustom"));
        return m;
    }

    static Map<String, String> experiences() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String e : new String[]{"0", "1-3", "3-10", "10+"}) m.put(e, I18n.t("plots.years", e.replace('-', '–')));
        return m;
    }

    private final MatchingService matching;
    private final PlotService plots;
    private final StatusLabels labels;
    private final Fmt fmt;

    @GetMapping("/uygunluq")
    public String quiz(@ModelAttribute("a") Answers a, @RequestParam(required = false) String edit,
                       HttpServletRequest request, Model model) {
        model.addAttribute("activities", ActivityType.values());
        model.addAttribute("budgets", budgets());
        model.addAttribute("sizes", sizes());
        model.addAttribute("districts", plots.districts());
        model.addAttribute("experiences", experiences());
        boolean submitted = a.activity() != null || a.budget() != null || a.size() != null || a.experience() != null;
        String problem = !submitted ? null : !a.complete() ? I18n.t("plots.quiz.answerAll") : a.invalid();
        if (problem != null) model.addAttribute("error", problem);
        if (submitted && problem == null && edit == null) {
            try {
                model.addAttribute("matches", matching.match(a));
                model.addAttribute("summary", summary(a));
                var editLink = org.springframework.web.util.UriComponentsBuilder.fromPath("/uygunluq");
                request.getParameterMap().forEach((k, v) -> editLink.queryParam(k, (Object[]) v));
                model.addAttribute("editUrl", editLink.replaceQueryParam("edit", 1).encode().build().toUriString());
            } catch (IllegalArgumentException e) {
                model.addAttribute("error", I18n.t("plots.quiz.badAnswers"));
            }
        }
        return "uygunluq";
    }

    /** "Seçiminiz: Digər — arıçılıq · 120 000 AZN · 10–30 ha · Füzuli · təcrübə 3–10 il" */
    String summary(Answers a) {
        String activity = labels.label(a.activity())
                + (a.activity() == ActivityType.OTHER ? " — " + a.otherText().strip() : "");
        String budget = a.budget() == BUDGET_CUSTOM ? fmt.money(a.effectiveBudget()) : budgets().get(a.budget());
        String size = CUSTOM_SIZE.equals(a.size()) ? I18n.t("plots.ha", a.minHa().strip() + "–" + a.maxHa().strip()) : sizes().get(a.size());
        String district = a.district().isEmpty() ? I18n.t("plots.quiz.anyDistrictSummary") : a.district();
        return I18n.t("plots.quiz.summary", activity, budget, size, district,
                experiences().getOrDefault(a.experience(), a.experience()));
    }
}

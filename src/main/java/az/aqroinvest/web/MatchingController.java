package az.aqroinvest.web;

import az.aqroinvest.domain.ActivityType;
import az.aqroinvest.service.MatchingService;
import az.aqroinvest.service.MatchingService.Answers;
import az.aqroinvest.service.PlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** Quiz and results on one GET URL so results are shareable. */
@Controller
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matching;
    private final PlotService plots;

    @GetMapping("/uygunluq")
    public String quiz(@ModelAttribute("a") Answers a, @RequestParam(required = false) String edit, Model model) {
        model.addAttribute("activities", ActivityType.values());
        model.addAttribute("budgets", Map.of(100_000L, "100 000", 250_000L, "250 000", 500_000L, "500 000",
                1_000_000L, "1 000 000", MatchingService.BUDGET_NO_LIMIT, "2 000 000+"));
        model.addAttribute("budgetOrder", List.of(100_000L, 250_000L, 500_000L, 1_000_000L, MatchingService.BUDGET_NO_LIMIT));
        model.addAttribute("sizes", List.of("5-20", "20-50", "50-150", "150+"));
        model.addAttribute("districts", plots.districts());
        model.addAttribute("experiences", List.of("0", "1-3", "3-10", "10+"));
        boolean submitted = a.activity() != null || a.budget() != null || a.size() != null || a.experience() != null;
        if (submitted && !a.complete()) model.addAttribute("error", "Zəhmət olmasa bütün 5 suala cavab verin.");
        if (a.complete() && edit == null) {
            try {
                model.addAttribute("matches", matching.match(a));
            } catch (IllegalArgumentException e) {
                model.addAttribute("error", "Cavablar düzgün deyil. Testi yenidən keçin.");
            }
        }
        return "uygunluq";
    }
}

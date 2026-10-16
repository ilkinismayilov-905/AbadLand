package az.aqroinvest.web;

import az.aqroinvest.ai.AiAnalysisService;
import az.aqroinvest.domain.AiState;
import az.aqroinvest.domain.EconomicRegion;
import az.aqroinvest.domain.Plot;
import az.aqroinvest.repo.PlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public AI center: what the land analysis does, parcel picker, stored results and the labelled sample.
 * Reads stored analyses only — never calls the model or fetches imagery; the one satellite image loads after a choice
 * (existing /saheler/{id}/peyk.jpg). Running an analysis stays on the passport (POST /saheler/{id}/ai-analiz, login).
 */
@Controller
@RequiredArgsConstructor
public class AiCenterController {

    private final PlotRepository plots;
    private final AiAnalysisService ai;

    @GetMapping("/ai")
    public String center(@RequestParam(required = false) String sahe, Model model) {
        List<Plot> published = plots.findAll(Sort.by("code")).stream().filter(Plot::isPublished).toList();
        Map<Long, AiState> states = ai.states();
        Map<EconomicRegion, List<Plot>> groups = new LinkedHashMap<>();
        published.forEach(p -> groups.computeIfAbsent(p.getEconomicRegion(), r -> new ArrayList<>()).add(p));
        model.addAttribute("groups", groups);
        List<Plot> analysed = published.stream().filter(p -> states.get(p.getId()) == AiState.AVAILABLE).toList();
        model.addAttribute("analysed", analysed);
        model.addAttribute("analysedIds", analysed.stream().map(Plot::getId).collect(java.util.stream.Collectors.toSet()));
        model.addAttribute("aiConfigured", ai.isConfigured());
        ai.sample().ifPresent(s -> {
            model.addAttribute("sample", ai.view(s).orElse(null));
            model.addAttribute("samplePlot", s.getPlot());
        });
        if (sahe != null && !sahe.isBlank()) {
            // Drafts are not in the list, so their codes read as "not found" (same rule as the passport's 404)
            Plot p = published.stream().filter(x -> x.getCode().equalsIgnoreCase(sahe.trim())).findFirst().orElse(null);
            model.addAttribute("notFound", p == null);
            if (p != null) {
                model.addAttribute("selected", p);
                model.addAttribute("selectedState", states.getOrDefault(p.getId(), AiState.NONE));
                ai.latestReal(p).ifPresent(a -> {
                    model.addAttribute("selectedAnalysis", a);
                    model.addAttribute("selectedAi", ai.view(a).orElse(null));
                });
            }
        }
        return "ai";
    }
}

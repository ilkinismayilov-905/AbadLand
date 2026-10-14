package az.aqroinvest.web;

import az.aqroinvest.domain.CompetitionStatus;
import az.aqroinvest.domain.Decision;
import az.aqroinvest.domain.Plot;
import az.aqroinvest.repo.ApplicationRepository;
import az.aqroinvest.repo.CompetitionRepository;
import az.aqroinvest.repo.PlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PublicController {

    private final PlotRepository plots;
    private final CompetitionRepository competitions;
    private final ApplicationRepository applications;

    @GetMapping("/")
    public String home(Model model) {
        List<Plot> all = plots.findAll();
        model.addAttribute("plotCount", all.size());
        model.addAttribute("totalHa", Math.round(all.stream().mapToDouble(Plot::getAreaHa).sum()));
        model.addAttribute("openCompetitions", competitions.countByStatus(CompetitionStatus.PUBLISHED));
        model.addAttribute("approvedInvestment",
                applications.findByDecision(Decision.APPROVED).stream().mapToLong(a -> a.getInvestmentAzn()).sum());
        return "index";
    }
}

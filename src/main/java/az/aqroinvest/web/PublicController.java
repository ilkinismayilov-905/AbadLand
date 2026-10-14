package az.aqroinvest.web;

import az.aqroinvest.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PublicController {

    private final DashboardService dashboard;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("home", dashboard.home());
        return "index";
    }
}

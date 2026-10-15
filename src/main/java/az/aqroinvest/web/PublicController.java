package az.aqroinvest.web;

import az.aqroinvest.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PublicController {

    /** Hero photo shipped with the app; without it the page keeps the SVG illustration. */
    static final boolean HERO_PHOTO = new org.springframework.core.io.ClassPathResource("static/img/hero-1200.webp").exists();

    private final DashboardService dashboard;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("home", dashboard.home());
        model.addAttribute("heroPhoto", HERO_PHOTO);
        return "index";
    }
}

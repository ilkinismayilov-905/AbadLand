package az.aqroinvest.web;

import az.aqroinvest.repo.ApplicationRepository;
import az.aqroinvest.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class InvestorController {

    private final UserRepository users;
    private final ApplicationRepository applications;

    @GetMapping("/kabinet")
    public String cabinet(Authentication auth, Model model) {
        model.addAttribute("user", users.findByEmailIgnoreCase(auth.getName()).orElseThrow());
        model.addAttribute("applications", applications.findByInvestorEmailIgnoreCaseOrderByCreatedAtDesc(auth.getName()));
        return "kabinet";
    }
}

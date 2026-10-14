package az.aqroinvest.web;

import az.aqroinvest.farm.FarmService;
import az.aqroinvest.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Staff monitoring of farm projects (read-only + report review). */
@Controller
@RequiredArgsConstructor
@RequestMapping("/idare/monitorinq")
public class MonitoringController {

    private final FarmService farms;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("views", farms.all());
        return "idare-monitorinq";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("v", farms.view(farms.get(id)));
        model.addAttribute("editable", false);
        return "idare-monitorinq-detal";
    }

    @PostMapping("/{id}/hesabat/{reportId}")
    public String review(@PathVariable Long id, @PathVariable Long reportId, @RequestParam(defaultValue = "false") boolean accept,
                         @RequestParam(required = false) String comment, Authentication auth, RedirectAttributes ra) {
        try {
            farms.reviewReport(reportId, auth.getName(), accept, comment);
            ra.addFlashAttribute("success", accept ? "Hesabat qəbul edildi." : "Düzəliş tələbi göndərildi.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/monitorinq/" + id + "#hesabat";
    }
}

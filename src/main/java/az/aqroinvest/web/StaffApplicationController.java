package az.aqroinvest.web;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.ApplicationRepository;
import az.aqroinvest.repo.CropRepository;
import az.aqroinvest.repo.DocumentRepository;
import az.aqroinvest.service.ApplicationService;
import az.aqroinvest.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Operator / commission / admin review of applications. Role checks live in ApplicationService. */
@Controller
@RequiredArgsConstructor
@RequestMapping("/idare")
public class StaffApplicationController {

    private final ApplicationRepository applications;
    private final DocumentRepository documents;
    private final CropRepository crops;
    private final ApplicationService service;

    @GetMapping("/muracietler")
    public String list(@RequestParam(required = false) ApplicationStatus status, Model model) {
        model.addAttribute("applications", status == null ? applications.findAllByOrderByUpdatedAtDesc()
                : applications.findByStatusOrderByUpdatedAtDesc(status));
        model.addAttribute("status", status);
        model.addAttribute("statuses", ApplicationStatus.values());
        return "idare-muracietler";
    }

    @GetMapping({"/muracietler/{id}", "/muraciet/{id}"})
    public String detail(@PathVariable Long id, Model model) {
        Application a = service.get(id);
        model.addAttribute("app", a);
        model.addAttribute("history", service.history(a));
        model.addAttribute("documents", documents.findByApplicationOrderByUploadedAtAsc(a));
        model.addAttribute("cropName", crops.findByCode(a.getPlannedCrop()).map(Crop::getName).orElse(a.getPlannedCrop()));
        return "idare-muraciet";
    }

    @PostMapping("/muracietler/{id}/baxis")
    public String startReview(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        return act(id, ra, I18n.t("staff.flash.reviewStarted"), () -> service.startReview(id, auth.getName()));
    }

    @PostMapping("/muracietler/{id}/melumat")
    public String requestInfo(@PathVariable Long id, @RequestParam(required = false) String message, Authentication auth,
                              RedirectAttributes ra) {
        return act(id, ra, I18n.t("staff.flash.infoRequested"), () -> service.requestInfo(id, auth.getName(), message));
    }

    @PostMapping("/muracietler/{id}/qerar")
    public String decide(@PathVariable Long id, @RequestParam(required = false) Decision decision,
                         @RequestParam(required = false) String reason, Authentication auth, RedirectAttributes ra) {
        return act(id, ra, I18n.t("staff.flash.decisionSaved"), () -> service.decide(id, auth.getName(), decision, reason));
    }

    private String act(Long id, RedirectAttributes ra, String ok, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("success", ok);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/muracietler/" + id;
    }
}

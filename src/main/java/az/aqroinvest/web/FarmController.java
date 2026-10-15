package az.aqroinvest.web;

import az.aqroinvest.domain.FarmProject;
import az.aqroinvest.domain.WorkRole;
import az.aqroinvest.farm.FarmService;
import az.aqroinvest.service.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

import static az.aqroinvest.service.AuditService.BAKU;

/** Investor farm management after approval. Ownership checked in FarmService (others → 404). */
@Controller
@RequiredArgsConstructor
@RequestMapping("/kabinet/teserrufat")
public class FarmController {

    private final FarmService farms;

    @GetMapping("/{id}")
    public String page(@PathVariable Long id, Authentication auth, Model model) {
        FarmProject f = farms.getOwned(id, auth.getName());
        model.addAttribute("v", farms.view(f));
        model.addAttribute("crops", farms.cropOptions(f));
        model.addAttribute("roles", WorkRole.values());
        model.addAttribute("today", LocalDate.now(BAKU));
        model.addAttribute("editable", true);
        return "teserrufat";
    }

    @PostMapping("/{id}/plan")
    public String addPlan(@PathVariable Long id, @RequestParam(required = false) String crop, @RequestParam(required = false) Double areaHa,
                          @RequestParam(required = false) Integer seasonYear, Authentication auth, RedirectAttributes ra) {
        return act(id, "plan", ra, I18n.t("apply.flash.planAdded"), () -> farms.addCropPlan(id, auth.getName(), crop, areaHa, seasonYear));
    }

    @PostMapping("/{id}/tapsiriq/{taskId}")
    public String markDone(@PathVariable Long id, @PathVariable Long taskId,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                           @RequestParam(required = false) String note, Authentication auth, RedirectAttributes ra) {
        return act(id, "teqvim", ra, I18n.t("apply.flash.taskDone"), () -> farms.markDone(taskId, auth.getName(), date, note));
    }

    @PostMapping("/{id}/isci")
    public String addWorkers(@PathVariable Long id, @RequestParam(required = false) WorkRole role, @RequestParam(required = false) Integer count,
                             @RequestParam(defaultValue = "false") boolean local, Authentication auth, RedirectAttributes ra) {
        return act(id, "heyet", ra, I18n.t("apply.flash.workforceUpdated"), () -> farms.addWorkers(id, auth.getName(), role, count, local));
    }

    @PostMapping("/{id}/hesabat")
    public String report(@PathVariable Long id, @RequestParam(required = false) String period, @RequestParam(required = false) Long investedToDate,
                         @RequestParam(required = false) String note, @RequestParam(required = false) List<MultipartFile> photos,
                         Authentication auth, RedirectAttributes ra) {
        return act(id, "hesabat", ra, I18n.t("apply.flash.reportSent"), () -> farms.submitReport(id, auth.getName(), period, investedToDate, note, photos));
    }

    private String act(Long id, String anchor, RedirectAttributes ra, String ok, Runnable action) {
        try {
            action.run();
            ra.addFlashAttribute("success", ok);
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/kabinet/teserrufat/" + id + "#" + anchor;
    }
}

package az.aqroinvest.web;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.ApplicationService;
import az.aqroinvest.service.ApplicationService.NewApplication;
import az.aqroinvest.service.AuditService;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class InvestorController {

    private final UserRepository users;
    private final ApplicationRepository applications;
    private final PlotRepository plots;
    private final CropRepository crops;
    private final DocumentRepository documents;
    private final ApplicationService service;
    private final StorageService storage;
    private final AuditService audit;

    @GetMapping("/kabinet")
    public String cabinet(Authentication auth, Model model) {
        User u = me(auth);
        model.addAttribute("user", u);
        if (!model.containsAttribute("profile")) {
            ProfileForm p = new ProfileForm();
            p.setFin(u.getFin());
            p.setVoen(u.getVoen());
            p.setExperienceYears(u.getExperienceYears());
            model.addAttribute("profile", p);
        }
        model.addAttribute("applications", applications.findByInvestorEmailIgnoreCaseOrderByCreatedAtDesc(auth.getName()));
        return "kabinet";
    }

    @PostMapping("/kabinet/profil")
    public String saveProfile(@Valid @ModelAttribute("profile") ProfileForm form, BindingResult br, Authentication auth,
                              Model model, RedirectAttributes ra) {
        User u = me(auth);
        if (u.getInvestorType() == InvestorType.LEGAL) {
            if (form.getVoen() == null || !form.getVoen().trim().matches("\\d{10}"))
                br.rejectValue("voen", "voen", "VÖEN 10 rəqəmdən ibarət olmalıdır");
        } else if (form.getFin() == null || !form.getFin().trim().toUpperCase().matches("[0-9A-Z]{7}")) {
            br.rejectValue("fin", "fin", "FİN 7 simvoldan (rəqəm və hərf) ibarət olmalıdır");
        }
        if (br.hasErrors()) return cabinet(auth, model);
        if (u.getInvestorType() == InvestorType.LEGAL) u.setVoen(form.getVoen().trim());
        else u.setFin(form.getFin().trim().toUpperCase());
        u.setExperienceYears(form.getExperienceYears());
        users.save(u);
        audit.log("PROFILE_UPDATE", "User", u.getId(), null);
        ra.addFlashAttribute("success", "Profil yeniləndi.");
        return "redirect:/kabinet";
    }

    @GetMapping("/kabinet/muraciet/yeni")
    public String newForm(@RequestParam Long plotId, Authentication auth, Model model, RedirectAttributes ra) {
        Plot plot = plots.findById(plotId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (service.openCompetition(plot) == null) {
            ra.addFlashAttribute("error", "Bu sahə üzrə açıq müsabiqə yoxdur.");
            return "redirect:/saheler/" + plotId;
        }
        ApplicationForm form = new ApplicationForm();
        form.setPlotId(plotId);
        if (plot.getRecommendedCrops() != null) form.setPlannedCrop(plot.getRecommendedCrops().split(",")[0]);
        return showForm(form, plot, auth, model);
    }

    @PostMapping("/kabinet/muraciet")
    public String submit(@Valid @ModelAttribute("form") ApplicationForm form, BindingResult br,
                         @RequestParam(required = false) MultipartFile businessPlanFile,
                         @RequestParam(required = false) MultipartFile otherFile,
                         Authentication auth, Model model, RedirectAttributes ra) {
        Plot plot = plots.findById(form.getPlotId() == null ? -1 : form.getPlotId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        NewApplication n = new NewApplication(form.getPlotId(), form.getPlannedCrop(), form.getInvestmentAzn(),
                form.getJobs(), form.getMonths(), form.getBusinessPlan());
        // collect everything at once: field validation, files, business rules
        if (businessPlanFile == null || businessPlanFile.isEmpty()) model.addAttribute("planFileError", "Biznes plan faylı tələb olunur");
        else model.addAttribute("planFileError", storage.check(businessPlanFile));
        model.addAttribute("otherFileError", storage.check(otherFile));
        Map<String, String> rules = service.checkSubmission(me(auth), n);
        rules.forEach((field, msg) -> {
            if (field.isEmpty()) br.reject("rule", msg);
            else if (!br.hasFieldErrors(field)) br.rejectValue(field, "rule", msg);
        });
        if (br.hasErrors() || model.getAttribute("planFileError") != null || model.getAttribute("otherFileError") != null) {
            model.addAttribute("error", "Formada səhvlər var. Zəhmət olmasa düzəldin və faylları yenidən seçin.");
            return showForm(form, plot, auth, model);
        }
        try {
            Application a = service.submit(auth.getName(), n, businessPlanFile, otherFile);
            ra.addFlashAttribute("created", a.getNumber());
            return "redirect:/kabinet/muraciet/" + a.getId();
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return showForm(form, plot, auth, model);
        }
    }

    @GetMapping("/kabinet/muraciet/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        Application a = service.getOwned(id, auth.getName());
        model.addAttribute("app", a);
        model.addAttribute("history", service.history(a));
        model.addAttribute("documents", documents.findByApplicationOrderByUploadedAtAsc(a));
        model.addAttribute("cropName", crops.findByCode(a.getPlannedCrop()).map(Crop::getNameAz).orElse(a.getPlannedCrop()));
        model.addAttribute("replyTypes", List.of(DocType.SOIL_ANALYSIS, DocType.IDENTITY, DocType.OTHER));
        return "muraciet";
    }

    @PostMapping("/kabinet/muraciet/{id}/cavab")
    public String respond(@PathVariable Long id, @RequestParam(required = false) String response,
                          @RequestParam(required = false) MultipartFile file, @RequestParam(required = false) DocType fileType,
                          Authentication auth, RedirectAttributes ra) {
        try {
            service.respond(id, auth.getName(), response, file, fileType);
            ra.addFlashAttribute("success", "Cavabınız göndərildi. Müraciət yenidən baxışdadır.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("draftResponse", response);
        }
        return "redirect:/kabinet/muraciet/" + id;
    }

    private String showForm(ApplicationForm form, Plot plot, Authentication auth, Model model) {
        model.addAttribute("form", form);
        model.addAttribute("plot", plot);
        model.addAttribute("competition", service.openCompetition(plot));
        model.addAttribute("crops", crops.findAll().stream().filter(c -> c.getActivityType() == plot.getPurpose()).toList());
        model.addAttribute("user", me(auth));
        return "muraciet-yeni";
    }

    private User me(Authentication auth) {
        return users.findByEmailIgnoreCase(auth.getName()).orElseThrow();
    }
}

package az.aqroinvest.web;

import az.aqroinvest.repo.CompetitionRepository;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.CompetitionService;
import az.aqroinvest.service.CompetitionService.NewCompetition;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;

@Controller
@RequiredArgsConstructor
@RequestMapping("/idare/musabiqeler")
public class StaffCompetitionController {

    private final CompetitionRepository competitions;
    private final CompetitionService service;

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new CompetitionForm());
        model.addAttribute("competitions", competitions.findAllByOrderByIdDesc().stream()
                .sorted(Comparator.comparing(c -> c.getStatus().ordinal())).toList());
        model.addAttribute("freePlots", service.freePlots());
        return "idare-musabiqeler";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CompetitionForm form, BindingResult br, Authentication auth,
                         Model model, RedirectAttributes ra) {
        NewCompetition n = new NewCompetition(form.getTitle(), form.getRequirements(), form.getMinInvestment(),
                form.getDeadline(), form.getPlotIds());
        service.check(n).forEach((field, msg) -> { if (!br.hasFieldErrors(field)) br.rejectValue(field, "rule", msg); });
        if (br.hasErrors()) {
            model.addAttribute("error", "Formada səhvlər var.");
            model.addAttribute("openForm", true);
            return list(model);
        }
        try {
            var c = service.create(n, auth.getName());
            ra.addFlashAttribute("success", "Müsabiqə yaradıldı və komissiyanın təsdiqinə göndərildi: " + c.getCode());
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/musabiqeler";
    }

    @PostMapping("/{id}/tesdiq")
    public String approve(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            service.approve(id, auth.getName());
            ra.addFlashAttribute("success", "Müsabiqə təsdiqləndi və dərc edildi.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/musabiqeler";
    }

    @PostMapping("/{id}/redd")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment, Authentication auth,
                         RedirectAttributes ra) {
        try {
            service.reject(id, auth.getName(), comment);
            ra.addFlashAttribute("success", "Müsabiqə rədd edildi.");
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/musabiqeler";
    }
}

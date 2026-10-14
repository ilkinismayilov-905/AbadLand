package az.aqroinvest.web;

import az.aqroinvest.domain.Role;
import az.aqroinvest.service.BusinessException;
import az.aqroinvest.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** /idare/istifadeciler — ADMIN only (SecurityConfig + service check). */
@Controller
@RequiredArgsConstructor
@RequestMapping("/idare/istifadeciler")
public class UserAdminController {

    private final UserAdminService service;

    @GetMapping
    public String list(Model model, Authentication auth) {
        if (!model.containsAttribute("form")) model.addAttribute("form", new StaffUserForm());
        model.addAttribute("users", service.all());
        model.addAttribute("staffRoles", new Role[]{Role.OPERATOR, Role.COMMISSION, Role.ADMIN});
        model.addAttribute("me", auth.getName());
        return "idare-istifadeciler";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") StaffUserForm form, BindingResult br, Authentication auth,
                         Model model, RedirectAttributes ra) {
        if (br.hasErrors()) {
            model.addAttribute("openForm", true);
            return list(model, auth);
        }
        try {
            String password = service.createStaff(form.getFullName(), form.getEmail(), form.getPhone(), form.getRole(), auth.getName());
            // flash = shown exactly once, then gone
            ra.addFlashAttribute("tempEmail", form.getEmail().trim().toLowerCase());
            ra.addFlashAttribute("tempPassword", password);
            return "redirect:/idare/istifadeciler";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("openForm", true);
            return list(model, auth);
        }
    }

    @PostMapping("/{id}/aktiv")
    public String toggle(@PathVariable Long id, @RequestParam boolean active, Authentication auth, RedirectAttributes ra) {
        try {
            var u = service.setActive(id, active, auth.getName());
            ra.addFlashAttribute("success", u.getEmail() + (active ? " aktivləşdirildi." : " deaktiv edildi və sistemdən çıxarıldı."));
        } catch (BusinessException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/idare/istifadeciler";
    }
}

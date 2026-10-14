package az.aqroinvest.web;

import az.aqroinvest.domain.InvestorType;
import az.aqroinvest.domain.Role;
import az.aqroinvest.domain.User;
import az.aqroinvest.repo.UserRepository;
import az.aqroinvest.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    /** /qeydiyyat always creates an INVESTOR: privileged fields are never bound from the request. */
    @org.springframework.web.bind.annotation.InitBinder("form")
    void registrationFields(org.springframework.web.bind.WebDataBinder binder) {
        binder.setDisallowedFields("role", "id", "passwordHash", "active");
    }

    @GetMapping("/giris")
    public String login() {
        return "giris";
    }

    @GetMapping("/qeydiyyat")
    public String registerForm(@ModelAttribute("form") RegistrationForm form) {
        return "qeydiyyat";
    }

    @PostMapping("/qeydiyyat")
    public String register(@Valid @ModelAttribute("form") RegistrationForm form, BindingResult br, RedirectAttributes ra) {
        String email = form.getEmail() == null ? "" : form.getEmail().trim().toLowerCase();
        if (form.getInvestorType() == InvestorType.INDIVIDUAL) {
            if (form.getFin() == null || !form.getFin().trim().toUpperCase().matches("[0-9A-Z]{7}"))
                br.rejectValue("fin", "fin", "FİN 7 simvoldan (rəqəm və hərf) ibarət olmalıdır");
        } else if (form.getInvestorType() == InvestorType.LEGAL) {
            if (form.getVoen() == null || !form.getVoen().trim().matches("\\d{10}"))
                br.rejectValue("voen", "voen", "VÖEN 10 rəqəmdən ibarət olmalıdır");
            if (form.getCompanyName() == null || form.getCompanyName().isBlank())
                br.rejectValue("companyName", "companyName", "Şirkətin adı tələb olunur");
        }
        if (!email.isEmpty() && users.existsByEmailIgnoreCase(email))
            br.rejectValue("email", "email", "Bu e-poçt artıq qeydiyyatdan keçib");
        if (br.hasErrors()) return "qeydiyyat";

        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(form.getPassword()));
        u.setFullName(form.getFullName().trim());
        u.setPhone(form.getPhone().trim());
        u.setRole(Role.INVESTOR);
        u.setInvestorType(form.getInvestorType());
        if (form.getInvestorType() == InvestorType.INDIVIDUAL) {
            u.setFin(form.getFin().trim().toUpperCase());
        } else {
            u.setVoen(form.getVoen().trim());
            u.setCompanyName(form.getCompanyName().trim());
        }
        users.save(u);
        audit.log(email, "INVESTOR", "REGISTER", "User", u.getId(), form.getInvestorType().name());
        ra.addFlashAttribute("success", "Qeydiyyat uğurla tamamlandı. İndi daxil ola bilərsiniz.");
        return "redirect:/giris";
    }
}

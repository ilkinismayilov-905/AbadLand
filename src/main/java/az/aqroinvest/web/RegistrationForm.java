package az.aqroinvest.web;

import az.aqroinvest.domain.InvestorType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegistrationForm {
    @NotBlank(message = "{common.register.fullNameRequired}")
    @Size(max = 120, message = "{common.register.fullNameTooLong}")
    private String fullName;
    @NotBlank(message = "{common.register.emailRequired}")
    @Email(message = "{common.register.emailInvalid}")
    private String email;
    @NotBlank(message = "{common.register.phoneRequired}")
    @Pattern(regexp = "\\+?[0-9 ()-]{7,20}", message = "{common.register.phoneInvalid}")
    private String phone;
    @NotBlank(message = "{common.register.passwordRequired}")
    @Size(min = 8, max = 72, message = "{common.register.passwordShort}")
    private String password;
    @NotNull(message = "{common.register.typeRequired}")
    private InvestorType investorType = InvestorType.INDIVIDUAL;
    private String fin;
    private String voen;
    private String companyName;
    @AssertTrue(message = "{common.register.consentRequired}")
    private boolean consent;
}

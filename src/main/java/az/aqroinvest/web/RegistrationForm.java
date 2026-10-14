package az.aqroinvest.web;

import az.aqroinvest.domain.InvestorType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegistrationForm {
    @NotBlank(message = "Ad və soyad tələb olunur")
    @Size(max = 120, message = "Ad çox uzundur")
    private String fullName;
    @NotBlank(message = "E-poçt tələb olunur")
    @Email(message = "E-poçt düzgün deyil")
    private String email;
    @NotBlank(message = "Telefon tələb olunur")
    @Pattern(regexp = "\\+?[0-9 ()-]{7,20}", message = "Telefon nömrəsi düzgün deyil")
    private String phone;
    @NotBlank(message = "Şifrə tələb olunur")
    @Size(min = 8, max = 72, message = "Şifrə ən azı 8 simvol olmalıdır")
    private String password;
    @NotNull(message = "İnvestor növünü seçin")
    private InvestorType investorType = InvestorType.INDIVIDUAL;
    private String fin;
    private String voen;
    private String companyName;
    @AssertTrue(message = "Şəxsi məlumatların emalına razılıq tələb olunur")
    private boolean consent;
}

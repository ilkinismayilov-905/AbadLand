package az.aqroinvest.web;

import az.aqroinvest.domain.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class StaffUserForm {
    @NotBlank(message = "Ad və soyad tələb olunur")
    @Size(max = 120, message = "Ad çox uzundur")
    private String fullName;
    @NotBlank(message = "E-poçt tələb olunur")
    @Email(message = "E-poçt düzgün deyil")
    private String email;
    @NotBlank(message = "Telefon tələb olunur")
    @Pattern(regexp = "\\+?[0-9 ()-]{7,20}", message = "Telefon nömrəsi düzgün deyil")
    private String phone;
    @NotNull(message = "Rolu seçin")
    private Role role;
}

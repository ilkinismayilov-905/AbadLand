package az.aqroinvest.web;

import az.aqroinvest.domain.Role;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class StaffUserForm {
    @NotBlank(message = "{staff.form.fullNameRequired}")
    @Size(max = 120, message = "{staff.form.nameTooLong}")
    private String fullName;
    @NotBlank(message = "{staff.form.emailRequired}")
    @Email(message = "{staff.form.emailInvalid}")
    private String email;
    @NotBlank(message = "{staff.form.phoneRequired}")
    @Pattern(regexp = "\\+?[0-9 ()-]{7,20}", message = "{staff.form.phoneInvalid}")
    private String phone;
    @NotNull(message = "{staff.form.roleRequired}")
    private Role role;
}

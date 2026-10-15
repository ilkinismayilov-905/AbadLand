package az.aqroinvest.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ProfileForm {
    private String fin;
    private String voen;
    @Min(value = 0, message = "{common.profile.experienceNegative}")
    @Max(value = 80, message = "{common.profile.experienceInvalid}")
    private Integer experienceYears;
}

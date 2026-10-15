package az.aqroinvest.web;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ApplicationForm {
    @NotNull(message = "{apply.form.plotRequired}")
    private Long plotId;
    @NotBlank(message = "{apply.form.cropRequired}")
    private String plannedCrop;
    @NotNull(message = "{apply.form.investmentRequired}")
    @Positive(message = "{apply.form.investmentPositive}")
    @Max(value = 1_000_000_000L, message = "{apply.form.amountTooLarge}")
    private Long investmentAzn;
    @NotNull(message = "{apply.form.jobsRequired}")
    @Min(value = 1, message = "{apply.form.jobsMin}")
    @Max(value = 10_000, message = "{apply.form.jobsMax}")
    private Integer jobs;
    @NotNull(message = "{apply.form.monthsRequired}")
    @Min(value = 1, message = "{apply.form.monthsMin}")
    @Max(value = 600, message = "{apply.form.monthsMax}")
    private Integer months;
    @NotBlank(message = "{apply.form.planRequired}")
    @Size(max = 3000, message = "{apply.form.planMax}")
    private String businessPlan;
    @AssertTrue(message = "{apply.form.consentRequired}")
    private boolean consent;
}

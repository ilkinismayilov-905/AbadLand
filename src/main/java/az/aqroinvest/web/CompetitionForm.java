package az.aqroinvest.web;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class CompetitionForm {
    @NotBlank(message = "{staff.form.titleRequired}")
    @Size(max = 200, message = "{staff.form.max200}")
    private String title;
    @NotBlank(message = "{staff.form.requirementsRequired}")
    @Size(max = 3000, message = "{staff.form.max3000}")
    private String requirements;
    @NotNull(message = "{staff.form.minInvestmentRequired}")
    @Positive(message = "{staff.form.positive}")
    private Long minInvestment;
    @NotNull(message = "{staff.form.deadlineRequired}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadline;
    private List<Long> plotIds = new ArrayList<>();
}

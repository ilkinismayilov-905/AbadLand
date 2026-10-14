package az.aqroinvest.web;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
public class CompetitionForm {
    @NotBlank(message = "Başlıq tələb olunur")
    @Size(max = 200, message = "Ən çox 200 simvol")
    private String title;
    @NotBlank(message = "Tələbləri yazın")
    @Size(max = 3000, message = "Ən çox 3000 simvol")
    private String requirements;
    @NotNull(message = "Minimum investisiyanı yazın")
    @Positive(message = "Müsbət olmalıdır")
    private Long minInvestment;
    @NotNull(message = "Son tarixi seçin")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate deadline;
    private List<Long> plotIds = new ArrayList<>();
}

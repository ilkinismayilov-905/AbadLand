package az.aqroinvest.web;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ApplicationForm {
    @NotNull
    private Long plotId;
    @NotBlank(message = "Bitkini seçin")
    private String plannedCrop;
    @NotNull(message = "İnvestisiya məbləğini yazın")
    @Positive(message = "İnvestisiya müsbət olmalıdır")
    @Max(value = 1_000_000_000L, message = "Məbləğ çox böyükdür")
    private Long investmentAzn;
    @NotNull(message = "İş yerlərinin sayını yazın")
    @Min(value = 1, message = "Ən azı 1 iş yeri")
    @Max(value = 10_000, message = "Çox böyük say")
    private Integer jobs;
    @NotNull(message = "Müddəti yazın")
    @Min(value = 1, message = "Ən azı 1 ay")
    @Max(value = 600, message = "Ən çox 600 ay")
    private Integer months;
    @NotBlank(message = "Biznes planın qısa təsvirini yazın")
    @Size(max = 3000, message = "Ən çox 3000 simvol")
    private String businessPlan;
    @AssertTrue(message = "Məlumatların düzgünlüyünü təsdiqləyin")
    private boolean consent;
}

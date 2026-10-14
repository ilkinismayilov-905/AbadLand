package az.aqroinvest.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** REST DTOs (never expose entities). */
public final class ApiDtos {
    private ApiDtos() {}

    public record PlotDto(String code, String name, String district, String economicRegion, double areaHa, String purpose,
                          String demining, boolean irrigation, boolean electricity, double roadKm, double lat, double lng,
                          String openCompetition) {}

    public record SuitabilityDto(String cropCode, String cropName, int score, String source, String reason) {}

    public record ClimateDto(Integer elevationM, Double meanTempC, Double absMinTempC, Integer frostDays, Double precipMm) {}

    public record PlotDetailDto(PlotDto plot, String village, String waterSource, String soilType, int leaseYears, int feePerHa,
                                String description, ClimateDto climate, List<SuitabilityDto> suitability) {}

    public record CompetitionDto(String code, String title, long minInvestment, LocalDate deadline, long daysLeft, List<String> plots) {}

    public record ApplicationStatusDto(String number, String status, String statusLabel, String decision, String ektisRef,
                                       LocalDateTime updatedAt) {}

    public record DecisionDto(String applicationNumber, String decision, String reason, long investmentAzn, int jobs,
                              String plotCode, String district, String competitionCode, String ektisRef, LocalDateTime decidedAt) {}
}

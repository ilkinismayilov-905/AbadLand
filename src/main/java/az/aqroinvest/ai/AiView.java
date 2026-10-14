package az.aqroinvest.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Parsed AI land analysis for templates (SPEC JSON shape). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiView(String summary, String landCover, String terrain, String waterSigns, List<String> risks,
                     List<Crop> suitableCrops, List<NotRecommended> notRecommended, String irrigationAdvice,
                     String confidence, List<String> dataGaps) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Crop(String code, String name, Integer score, String reason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotRecommended(String name, String reason) {}

    public String confidenceLabel() {
        return confidence == null ? "—" : switch (confidence) {
            case "HIGH" -> "Yüksək";
            case "MEDIUM" -> "Orta";
            case "LOW" -> "Aşağı";
            default -> confidence;
        };
    }
}

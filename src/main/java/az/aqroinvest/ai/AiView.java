package az.aqroinvest.ai;

import az.aqroinvest.domain.RiskLevel;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Parsed AI land analysis for templates (prompts/land-analysis.txt JSON shape). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiView(String summary, String landCover, String terrain, String waterSigns, List<String> observations,
                     List<Risk> risks, List<Crop> suitableCrops, List<NotRecommended> notRecommended, String irrigationAdvice,
                     List<String> recommendedChecks, String confidence, List<String> dataGaps) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Crop(String code, String name, Integer score, String reason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotRecommended(String name, String reason) {}

    /** Current prompt: {"title","level","reason"}. Older analyses (seed sample, cached results) have a plain string. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Risk(String title, String level, String reason) {

        @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
        public Risk(@JsonProperty("title") String title, @JsonProperty("level") String level, @JsonProperty("reason") String reason) {
            this.title = title;
            this.level = level;
            this.reason = reason;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Risk ofText(String text) {
            return new Risk(text, null, null);
        }

        /** LOW / MEDIUM / HIGH as the shared enum (labels + badge colours via StatusLabels); null if absent or unknown. */
        public RiskLevel levelEnum() {
            if (level == null) return null;
            try {
                return RiskLevel.valueOf(level.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        public String text() {
            return reason == null || reason.isBlank() ? title : title + " — " + reason;
        }
    }

    public String confidenceLabel() {
        return confidence == null ? "—" : switch (confidence) {
            case "HIGH", "MEDIUM", "LOW" -> az.aqroinvest.web.I18n.t("plots.ai.confidence." + confidence);
            default -> confidence;
        };
    }
}

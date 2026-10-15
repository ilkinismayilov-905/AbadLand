package az.aqroinvest.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Set;

/**
 * Checks the model's JSON against prompts/land-analysis.txt before it is stored: all keys present, scores 0–100,
 * levels LOW|MEDIUM|HIGH. Unknown crop codes are not an error — they become null (the crop then has no catalog score).
 */
public final class AiResultValidator {

    /** Answer does not match the schema → worth one retry. The reason is for logs only (never shown, never contains the key). */
    public static class Invalid extends AiException {
        public Invalid(String reason) {
            super(reason);
        }
    }

    static final List<String> TEXT_KEYS = List.of("summary", "landCover", "terrain", "waterSigns", "irrigationAdvice");
    static final List<String> LIST_KEYS = List.of("observations", "suitableCrops", "notRecommended", "risks", "recommendedChecks", "dataGaps");
    static final Set<String> LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private AiResultValidator() {}

    /** @return normalized JSON (unknown crop codes → null) */
    public static String normalize(String text, Set<String> allowedCodes, ObjectMapper json) {
        JsonNode root;
        try {
            root = json.readTree(text);
        } catch (Exception e) {
            throw new Invalid("not JSON");
        }
        if (root == null || !root.isObject()) throw new Invalid("not an object");
        ObjectNode o = (ObjectNode) root;
        for (String k : TEXT_KEYS) if (!o.path(k).isTextual()) throw new Invalid("missing text " + k);
        if (o.path("summary").asText().isBlank()) throw new Invalid("empty summary");
        for (String k : LIST_KEYS) if (!o.path(k).isArray()) throw new Invalid("missing list " + k);
        if (!LEVELS.contains(o.path("confidence").asText())) throw new Invalid("confidence");
        for (JsonNode c : o.path("suitableCrops")) {
            if (!c.isObject() || !c.path("name").isTextual() || c.path("name").asText().isBlank()) throw new Invalid("crop name");
            if (!c.path("score").canConvertToInt() || !c.path("score").isIntegralNumber()) throw new Invalid("crop score");
            int score = c.path("score").asInt();
            if (score < 0 || score > 100) throw new Invalid("crop score range");
            JsonNode code = c.path("code");
            if (!code.isNull() && !(code.isTextual() && allowedCodes.contains(code.asText()))) ((ObjectNode) c).putNull("code");
        }
        for (JsonNode n : o.path("notRecommended"))
            if (!n.isObject() || !n.path("name").isTextual()) throw new Invalid("notRecommended");
        for (JsonNode r : o.path("risks"))
            if (!r.isObject() || !r.path("title").isTextual() || !LEVELS.contains(r.path("level").asText())) throw new Invalid("risk");
        return o.toString();
    }
}

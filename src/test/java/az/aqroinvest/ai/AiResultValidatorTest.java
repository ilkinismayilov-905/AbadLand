package az.aqroinvest.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Schema validation + the single retry, with a fake AI client (no network, no API key). */
class AiResultValidatorTest {

    final ObjectMapper json = new ObjectMapper();
    final Set<String> codes = Set.of("GRAPE", "APPLE");

    static String answer(String cropCode, int score, String level) {
        return """
                {"summary":"Qısa nəticə","landCover":"Ot","terrain":"Düz","waterSigns":"Kanal","observations":["Yol var"],
                 "suitableCrops":[{"code":%s,"name":"Üzüm","score":%d,"reason":"İqlim uyğundur"}],"notRecommended":[],
                 "risks":[{"title":"Şaxta","level":"%s","reason":"Mütləq minimum aşağıdır"}],"irrigationAdvice":"Damcı",
                 "recommendedChecks":["Torpaq analizi"],"confidence":"MEDIUM","dataGaps":[]}"""
                .formatted(cropCode == null ? "null" : "\"" + cropCode + "\"", score, level);
    }

    @Test
    void validAnswerPassesUnchanged() throws Exception {
        String out = AiResultValidator.normalize(answer("GRAPE", 84, "HIGH"), codes, json);
        assertThat(json.readTree(out).path("suitableCrops").path(0).path("code").asText()).isEqualTo("GRAPE");
        assertThat(json.readValue(out, AiView.class).risks().get(0).levelEnum()).isNotNull();
    }

    @Test
    void unknownCropCodeBecomesNull() throws Exception {
        String out = AiResultValidator.normalize(answer("MANGO", 50, "LOW"), codes, json);
        assertThat(json.readTree(out).path("suitableCrops").path(0).path("code").isNull()).isTrue();
        assertThat(json.readTree(out).path("suitableCrops").path(0).path("name").asText()).isEqualTo("Üzüm"); // kept by name
    }

    @Test
    void brokenOrOffSchemaAnswersAreInvalid() {
        assertThatThrownBy(() -> AiResultValidator.normalize("{\"summary\": \"yarımçıq", codes, json)).isInstanceOf(AiResultValidator.Invalid.class);
        assertThatThrownBy(() -> AiResultValidator.normalize(answer("GRAPE", 120, "LOW"), codes, json)).hasMessageContaining("score");
        assertThatThrownBy(() -> AiResultValidator.normalize(answer("GRAPE", 70, "CRITICAL"), codes, json)).hasMessageContaining("risk");
        assertThatThrownBy(() -> AiResultValidator.normalize(answer("GRAPE", 70, "LOW").replace("\"dataGaps\":[]", "\"x\":1"), codes, json))
                .hasMessageContaining("dataGaps");
        assertThatThrownBy(() -> AiResultValidator.normalize(answer("GRAPE", 70, "LOW").replace("\"MEDIUM\"", "\"SURE\""), codes, json))
                .hasMessageContaining("confidence");
        assertThatThrownBy(() -> AiResultValidator.normalize("[1,2]", codes, json)).isInstanceOf(AiResultValidator.Invalid.class);
    }

    /** Fake client returning the given answers in order and recording what it was sent. */
    static AiClient fake(List<String> sent, Object... answers) {
        return new AiClient() {
            public boolean isConfigured() { return true; }
            public String model() { return "fake"; }
            public String analyze(byte[] jpeg, String userText) {
                Object a = answers[sent.size()];
                sent.add(userText);
                if (a instanceof RuntimeException e) throw e;
                return (String) a;
            }
        };
    }

    @Test
    void invalidFirstAnswerIsRetriedOnceWithTheNote() {
        List<String> sent = new ArrayList<>();
        String out = AiAnalysisService.analyzeValid(fake(sent, "bu JSON deyil", answer("APPLE", 77, "MEDIUM")), null, "faktlar", codes, json);
        assertThat(out).contains("APPLE");
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1)).startsWith("faktlar").endsWith(AiAnalysisService.RETRY_NOTE);
    }

    @Test
    void twoInvalidAnswersFailPolitelyAndOtherErrorsAreNotRetried() {
        List<String> sent = new ArrayList<>();
        assertThatThrownBy(() -> AiAnalysisService.analyzeValid(fake(sent, "{}", "{}", "{}"), null, "t", codes, json))
                .isInstanceOf(AiException.class).isNotInstanceOf(AiResultValidator.Invalid.class)
                .hasMessageContaining("iki cəhddən sonra");
        assertThat(sent).hasSize(2); // exactly one retry

        List<String> sent2 = new ArrayList<>();
        assertThatThrownBy(() -> AiAnalysisService.analyzeValid(fake(sent2, new AiException("HTTP 401")), null, "t", codes, json))
                .hasMessage("HTTP 401");
        assertThat(sent2).hasSize(1); // auth / rate limit / timeout: no second (billable) call
    }
}

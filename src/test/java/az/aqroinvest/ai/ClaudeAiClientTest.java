package az.aqroinvest.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeAiClientTest {

    final ObjectMapper json = new ObjectMapper();

    ClaudeAiClient client(String key, String model) throws Exception {
        return new ClaudeAiClient(null, json, key, model);
    }

    @Test
    void requestHasImageFirstThenTextAndNoSampling() throws Exception {
        JsonNode b = client("k", "claude-haiku-5-5").body("claude-haiku-5-5", "SYS", new byte[]{(byte) 0xFF, (byte) 0xD8, 1}, "facts");
        assertThat(b.path("model").asText()).isEqualTo("claude-haiku-5-5");
        assertThat(b.path("max_tokens").asLong()).isEqualTo(ClaudeAiClient.MAX_TOKENS);
        assertThat(b.path("system").asText()).isEqualTo("SYS");
        JsonNode content = b.path("messages").path(0).path("content");
        assertThat(b.path("messages").path(0).path("role").asText()).isEqualTo("user");
        assertThat(content.path(0).path("type").asText()).isEqualTo("image");
        assertThat(content.path(0).path("source").path("media_type").asText()).isEqualTo("image/jpeg");
        assertThat(content.path(0).path("source").path("data").asText()).isEqualTo("/9gB");
        assertThat(content.path(1).path("text").asText()).isEqualTo("facts");
        assertThat(b.has("temperature")).isFalse();
        assertThat(b.has("fallbacks")).isFalse(); // Haiku 5.5 has no server-side fallback
        assertThat(client("k", "claude-sonnet-5-5").body("claude-sonnet-5-5", "S", null, "t").path("fallbacks").asText()).isEqualTo("default");
    }

    @Test
    void noImageMeansTextOnly() throws Exception {
        JsonNode content = client("k", "m").body("m", "S", null, "t").path("messages").path(0).path("content");
        assertThat(content).hasSize(1);
        assertThat(content.path(0).path("type").asText()).isEqualTo("text");
    }

    @Test
    void extractsJsonFromTextBlocksIgnoringThinkingAndFences() throws Exception {
        String resp = """
                {"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},
                 {"type":"text","text":"```json\\n{\\"summary\\":\\"ok\\",\\"risks\\":[]}\\n```"}]}""";
        assertThat(client("k", "m").extractJson(resp)).isEqualTo("{\"summary\":\"ok\",\"risks\":[]}");
    }

    @Test
    void refusalMaxTokensAndNoJsonAreErrors() throws Exception {
        ClaudeAiClient c = client("k", "m");
        assertThatThrownBy(() -> c.extractJson("{\"stop_reason\":\"refusal\",\"content\":[]}")).isInstanceOf(AiException.class).hasMessageContaining("təhlükəsizlik");
        assertThatThrownBy(() -> c.extractJson("{\"stop_reason\":\"max_tokens\",\"content\":[{\"type\":\"text\",\"text\":\"{\\\"a\\\":\"}]}"))
                .hasMessageContaining("yarımçıq");
        assertThatThrownBy(() -> c.extractJson("{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"Bağışlayın\"}]}"))
                .hasMessageContaining("JSON");
    }

    @Test
    void notConfiguredWithoutKey() throws Exception {
        assertThat(client("", "m").isConfigured()).isFalse();
        assertThat(client("  ", "m").isConfigured()).isFalse();
        assertThatThrownBy(() -> client("", "m").analyze(null, "t")).hasMessageContaining("konfiqurasiya");
    }

    @Test
    void climateStatsFromArchive() throws Exception {
        JsonNode archive = json.readTree("""
                {"daily":{"temperature_2m_mean":[1.0,null,5.0],"temperature_2m_min":[-3.5,-0.1,2.0],"precipitation_sum":[1.2,null,0.8]}}""");
        ClimateService.Stats s = ClimateService.stats(archive);
        assertThat(s.meanTempC()).isEqualTo(3.0);
        assertThat(s.absMinTempC()).isEqualTo(-3.5);
        assertThat(s.frostDays()).isEqualTo(2);
        assertThat(s.precipMm()).isEqualTo(2.0);
    }
}

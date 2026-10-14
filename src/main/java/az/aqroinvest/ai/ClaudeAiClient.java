package az.aqroinvest.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * Claude Messages API over raw HTTP (CLAUDE.md: java.net.http only). Key from the ANTHROPIC_API_KEY env var only;
 * it is never logged or rendered.
 */
@Slf4j
@Component
public class ClaudeAiClient implements AiClient {

    static final String URL = "https://api.anthropic.com/v1/messages";
    /** Thinking is on by default for current models and counts toward max_tokens — keep room for the JSON. */
    static final long MAX_TOKENS = 4000;
    /** Models that take the server-side refusal fallback (Claude API). Haiku 5.5 has none. */
    static final Set<String> FALLBACK_MODELS = Set.of("claude-sonnet-5-5", "claude-opus-5-5", "claude-opus-5", "claude-fable-5-1");

    private final HttpClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;
    private final String systemPrompt;

    public ClaudeAiClient(HttpClient http, ObjectMapper json,
                          @Value("${ANTHROPIC_API_KEY:}") String apiKey,
                          @Value("${ai.model}") String model) throws IOException {
        this.http = http;
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.systemPrompt = new ClassPathResource("prompts/land-analysis.txt").getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String analyze(byte[] jpeg, String userText) {
        if (!isConfigured()) throw new AiException("AI açarı konfiqurasiya edilməyib");
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(90)) // image analysis takes ~10–30 s; connect timeout is 10 s
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body(model, systemPrompt, jpeg, userText).toString(), StandardCharsets.UTF_8));
        if (FALLBACK_MODELS.contains(model)) req.header("anthropic-beta", "server-side-fallback-2026-07-01");
        HttpResponse<String> r;
        try {
            r = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AiException("AI cavab vermədi (vaxt bitdi). Bir az sonra yenidən cəhd edin.");
        } catch (IOException e) {
            throw new AiException("AI xidmətinə qoşulmaq mümkün olmadı.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("AI sorğusu dayandırıldı.");
        }
        if (r.statusCode() != 200) throw httpError(r.statusCode(), r.body());
        return extractJson(r.body());
    }

    /** Request JSON: system prompt, one user turn with the image block first, then the text. No temperature (not allowed). */
    ObjectNode body(String model, String system, byte[] jpeg, String userText) {
        ObjectNode b = json.createObjectNode();
        b.put("model", model);
        b.put("max_tokens", MAX_TOKENS);
        b.put("system", system);
        b.putObject("output_config").put("effort", "low");
        if (FALLBACK_MODELS.contains(model)) b.put("fallbacks", "default");
        ObjectNode msg = b.putArray("messages").addObject();
        msg.put("role", "user");
        ArrayNode content = msg.putArray("content");
        if (jpeg != null) {
            ObjectNode img = content.addObject();
            img.put("type", "image");
            ObjectNode src = img.putObject("source");
            src.put("type", "base64");
            src.put("media_type", "image/jpeg");
            src.put("data", Base64.getEncoder().encodeToString(jpeg));
        }
        content.addObject().put("type", "text").put("text", userText);
        return b;
    }

    /** Checks stop_reason first, joins the text blocks (thinking blocks ignored), strips ``` fences, returns the JSON object text. */
    String extractJson(String responseBody) {
        JsonNode r;
        try {
            r = json.readTree(responseBody);
        } catch (IOException e) {
            throw new AiException("AI cavabı oxunmadı.");
        }
        String stop = r.path("stop_reason").asText("");
        if (stop.equals("refusal")) throw new AiException("AI bu sorğunu cavablandırmadı (təhlükəsizlik filtri).");
        if (stop.equals("max_tokens")) throw new AiException("AI cavabı yarımçıq qaldı. Yenidən cəhd edin.");
        StringBuilder text = new StringBuilder();
        for (JsonNode block : r.path("content")) if (block.path("type").asText().equals("text")) text.append(block.path("text").asText());
        return jsonObject(text.toString());
    }

    static String jsonObject(String text) {
        String t = text.trim().replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        int start = t.indexOf('{'), end = t.lastIndexOf('}');
        if (start < 0 || end <= start) throw new AiException("AI JSON formatında cavab vermədi.");
        return t.substring(start, end + 1);
    }

    private AiException httpError(int status, String body) {
        String type = "";
        try {
            type = json.readTree(body).path("error").path("type").asText("");
        } catch (IOException ignored) {
            // non-JSON error body
        }
        log.warn("Claude API error: HTTP {} {}", status, type); // never log the body or the key
        return new AiException(switch (status) {
            case 401, 403 -> "AI açarı etibarsızdır və ya icazəsi yoxdur.";
            case 429 -> "AI limitinə çatıldı. Bir dəqiqə sonra yenidən cəhd edin.";
            case 400 -> "AI sorğusu qəbul edilmədi (" + type + ").";
            default -> status >= 500 ? "AI xidməti hazırda əlçatan deyil (HTTP " + status + "). Sonra yenidən cəhd edin." : "AI xətası: HTTP " + status;
        });
    }
}

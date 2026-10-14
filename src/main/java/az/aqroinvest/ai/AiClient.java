package az.aqroinvest.ai;

/** Land-analysis model call. Returns the model's JSON text (fences stripped). */
public interface AiClient {
    boolean isConfigured();

    String model();

    /** @param jpeg satellite image or null */
    String analyze(byte[] jpeg, String userText);
}

package az.aqroinvest.ai;

/** AI call failed; message is Azerbaijani and safe to show (no key, no raw response). */
public class AiException extends RuntimeException {
    public AiException(String message) {
        super(message);
    }
}

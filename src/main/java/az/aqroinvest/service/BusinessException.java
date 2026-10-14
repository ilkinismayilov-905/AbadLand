package az.aqroinvest.service;

/** Rule violation with an Azerbaijani message shown to the user (flash or form). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}

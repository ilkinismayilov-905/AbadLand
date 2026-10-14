package az.aqroinvest.integration;

/** EKTİS (Ministry system) adapter. Chosen by ektis.mode: MOCK (default) or HTTP. */
public interface EktisClient {
    EktisResult send(String payloadJson);

    String mode();
}

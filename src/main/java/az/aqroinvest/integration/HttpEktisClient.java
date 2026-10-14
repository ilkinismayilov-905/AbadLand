package az.aqroinvest.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Real EKTİS over HTTP (ektis.mode=HTTP). Stub until the Ministry API contract is available. */
@Component
@ConditionalOnProperty(name = "ektis.mode", havingValue = "HTTP")
public class HttpEktisClient implements EktisClient {

    @Override
    public EktisResult send(String payloadJson) {
        // TODO: POST payloadJson to the EKTİS endpoint (java.net.http.HttpClient, 10 s timeout, auth per Ministry spec)
        return new EktisResult(false, null, "{\"status\":\"FAILED\",\"error\":\"NOT_CONFIGURED\",\"message\":\"HTTP EKTİS client is not implemented\"}");
    }

    @Override
    public String mode() {
        return "HTTP";
    }
}

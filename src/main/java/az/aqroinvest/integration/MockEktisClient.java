package az.aqroinvest.integration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Year;
import java.util.concurrent.ThreadLocalRandom;

/** Simulated EKTİS: 300 ms latency, ACCEPTED with a reference; fail flag (ektis.mock.fail, admin-toggleable) → FAILED. */
@Component
@ConditionalOnProperty(name = "ektis.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockEktisClient implements EktisClient {

    private volatile boolean fail;

    public MockEktisClient(@Value("${ektis.mock.fail:false}") boolean fail) {
        this.fail = fail;
    }

    @Override
    public EktisResult send(String payloadJson) {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (fail) return new EktisResult(false, null, "{\"status\":\"FAILED\",\"error\":\"EKTIS_UNAVAILABLE\",\"message\":\"Service temporarily unavailable (mock)\"}");
        String ref = "EKTIS-INV-" + Year.now().getValue() + "-" + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        return new EktisResult(true, ref, "{\"status\":\"ACCEPTED\",\"ektisReference\":\"" + ref + "\"}");
    }

    @Override
    public String mode() {
        return "MOCK";
    }

    public boolean isFail() {
        return fail;
    }

    public void setFail(boolean fail) {
        this.fail = fail;
    }
}

package az.aqroinvest.integration;

import az.aqroinvest.domain.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Sends commission decisions to EKTİS. Step 5 implements the mock/HTTP clients and IntegrationLog. */
@Slf4j
@Service
public class EktisService {

    public void sendDecision(Application app) {
        log.info("EKTİS sendDecision for {} — not implemented yet (Step 5)", app.getNumber());
    }
}

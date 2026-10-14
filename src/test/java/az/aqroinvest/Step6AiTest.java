package az.aqroinvest;

import az.aqroinvest.ai.AiClient;
import az.aqroinvest.repo.AiAnalysisRepository;
import az.aqroinvest.repo.PlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** AI flow with a fake model (no network: app.external.enabled=false). */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:step6;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.external.enabled=false", "app.upload-dir=target/test-uploads6"})
class Step6AiTest {

    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicBoolean configured = new AtomicBoolean(true);

    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        AiClient fakeAiClient() {
            return new AiClient() {
                public boolean isConfigured() { return configured.get(); }
                public String model() { return "fake-model"; }
                public String analyze(byte[] jpeg, String userText) {
                    calls.incrementAndGet();
                    assertThat(userText).contains("Allowed crop codes", "Satellite image is NOT available");
                    return """
                            {"summary":"Dağ yamacında bağ üçün yararlı sahə.","landCover":"Ot örtüyü","terrain":"Mülayim yamac",
                             "waterSigns":"Çay yaxındır","risks":["Yazda gec şaxta"],
                             "suitableCrops":[{"code":"APPLE","name":"Alma","score":90,"reason":"Hündürlük uyğundur"},
                                              {"code":"WALNUT","name":"Qoz","score":82,"reason":"Şaxtaya davamlı"}],
                             "notRecommended":[{"name":"Nar","reason":"Soyuq qış"}],"irrigationAdvice":"Damcı suvarma",
                             "confidence":"MEDIUM","dataGaps":["Torpaq analizi yoxdur"]}""";
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired PlotRepository plots;
    @Autowired AiAnalysisRepository analyses;

    static final RequestPostProcessor ELVIN = user("elvin@demo.az").roles("INVESTOR");

    long id(String code) {
        return plots.findByCode(code).orElseThrow().getId();
    }

    @Test
    void analysisFlowCacheLimitAndNoKey() throws Exception {
        long lcn = id("LCN-001");
        mvc.perform(get("/saheler/" + lcn)).andExpect(status().isOk()).andExpect(content().string(allOf(
                containsString("Peyk və AI analizi"), containsString("daxil olun"), containsString("Sınaq dəyəri — Open-Meteo"),
                containsString("Hündürlük"), containsString("Şaxtalı günlər"), containsString("AI tövsiyəsidir"))));
        // anonymous browser POST → login page
        mvc.perform(post("/saheler/" + lcn + "/ai-analiz").accept(org.springframework.http.MediaType.TEXT_HTML).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/giris"));

        // first analysis calls the model, second is served from cache
        mvc.perform(post("/saheler/" + lcn + "/ai-analiz").with(ELVIN).with(csrf())).andExpect(flash().attribute("success", "AI analizi hazırdır."));
        assertThat(calls.get()).isEqualTo(1);
        long t0 = System.currentTimeMillis();
        mvc.perform(post("/saheler/" + lcn + "/ai-analiz").with(ELVIN).with(csrf())).andExpect(flash().attributeExists("success"));
        assertThat(System.currentTimeMillis() - t0).isLessThan(500);
        assertThat(calls.get()).isEqualTo(1);

        mvc.perform(get("/saheler/" + lcn).with(ELVIN)).andExpect(content().string(allOf(
                containsString("Alma 90%"), containsString("· AI"), containsString("Dağ yamacında"), containsString("✗ Nar — Soyuq qış"),
                containsString("fake-model"), containsString("AI analizinə bax (keşdə)"),
                containsString("750 000 AZN")))); // ROI pre-selects AI top crop: 30 ha × 25 000 (Alma)

        // FZL-001 keeps the sample label and its demo default crop (Fındıq)
        mvc.perform(get("/saheler/" + id("FZL-001"))).andExpect(content().string(allOf(containsString("Nümunə analiz"), containsString("382 500 AZN"))));

        // daily limit: 5 analyses per user (1 done above)
        for (String code : new String[]{"FZL-002", "CBR-001", "ZNG-001", "AGD-002"})
            mvc.perform(post("/saheler/" + id(code) + "/ai-analiz").with(ELVIN).with(csrf())).andExpect(flash().attributeExists("success"));
        mvc.perform(post("/saheler/" + id("SHS-001") + "/ai-analiz").with(ELVIN).with(csrf()))
                .andExpect(flash().attribute("error", containsString("limitinə")));
        // investor cannot force a re-run; admin can
        mvc.perform(post("/saheler/" + lcn + "/ai-analiz").param("yeniden", "1").with(user("rashad@demo.az").roles("INVESTOR")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("admin")));
        mvc.perform(post("/saheler/" + lcn + "/ai-analiz").param("yeniden", "1").with(user("admin@demo.az").roles("ADMIN")).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(calls.get()).isEqualTo(6);

        // no key: page still works, button disabled, POST explains
        configured.set(false);
        mvc.perform(get("/saheler/" + id("SHS-001")).with(ELVIN)).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("AI açarı konfiqurasiya edilməyib"), containsString("disabled"))));
        mvc.perform(post("/saheler/" + id("SHS-001") + "/ai-analiz").with(user("rashad@demo.az").roles("INVESTOR")).with(csrf()))
                .andExpect(flash().attribute("error", containsString("konfiqurasiya")));
        configured.set(true);
    }
}

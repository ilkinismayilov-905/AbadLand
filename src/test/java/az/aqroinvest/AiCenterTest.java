package az.aqroinvest;

import az.aqroinvest.ai.AiAnalysisService;
import az.aqroinvest.ai.AiClient;
import az.aqroinvest.domain.AiAnalysis;
import az.aqroinvest.domain.ListingStatus;
import az.aqroinvest.domain.Plot;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public AI center (/ai): open to everyone, reads stored analyses only, never calls the model. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aicenter;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.external.enabled=false", "app.upload-dir=target/test-uploads-ai"})
class AiCenterTest {

    static final AtomicInteger calls = new AtomicInteger();
    static final AtomicBoolean configured = new AtomicBoolean(true);

    @TestConfiguration
    static class CountingAi {
        @Bean
        @Primary
        AiClient countingAiClient() {
            return new AiClient() {
                public boolean isConfigured() { return configured.get(); }
                public String model() { return "fake-model"; }
                public String analyze(byte[] jpeg, String userText) {
                    calls.incrementAndGet();
                    throw new IllegalStateException("GET /ai must never call the model");
                }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired PlotRepository plots;
    @Autowired AiAnalysisRepository analyses;
    @Autowired AiAnalysisService aiService;

    static final RequestPostProcessor INVESTOR = user("elvin@demo.az").roles("INVESTOR");

    String html(String url, RequestPostProcessor... who) throws Exception {
        var req = get(url);
        for (var w : who) req = req.with(w);
        return mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    Plot plot(String code) {
        return plots.findByCode(code).orElseThrow();
    }

    static int count(String html, String needle) {
        return html.split(Pattern.quote(needle), -1).length - 1;
    }

    @Test
    void openToEveryRoleAndLightOnFirstLoad() throws Exception {
        long rowsBefore = analyses.count();
        for (RequestPostProcessor who : new RequestPostProcessor[]{INVESTOR, user("operator@demo.az").roles("OPERATOR"),
                user("komissiya@demo.az").roles("COMMISSION"), user("admin@demo.az").roles("ADMIN")})
            assertThat(html("/ai", who)).contains("Sahəni peykdən oxuyan AI köməkçisi");
        String page = html("/ai");
        assertThat(page).contains("Sahəni peykdən oxuyan AI köməkçisi", "AI tövsiyəsidir · qərar deyil", "Nə etmir:",
                "NDVI", "Nəticələri necə oxumalı", "AI tövsiyəsidir: peyk görüntüsü", "/xerite?qat=peyk");
        // No map, 3D or satellite image until a plot is chosen
        assertThat(page).doesNotContain("peyk.jpg", "leaflet", "three.min.js", "plot3d", "data-ai-state");
        assertThat(calls.get()).isZero();
        assertThat(analyses.count()).isEqualTo(rowsBefore);
    }

    @Test
    void allThreeLanguagesWithoutMissingKeys() throws Exception {
        assertThat(html("/ai")).doesNotContain("??");
        assertThat(html("/ai?lang=en&sahe=FZL-001")).contains("An AI assistant that reads land from satellite imagery",
                "Sample analysis — demo data, not a real AI call", "How to read the results").doesNotContain("??");
        assertThat(html("/ai?lang=ru&sahe=FZL-001")).contains("ИИ-помощник, который читает участок по спутниковому снимку",
                "Образец анализа — демо-данные", "Как читать результаты").doesNotContain("??");
    }

    @Test
    void sampleIsLabelledAndNeverShownAsReal() throws Exception {
        long fzl = plot("FZL-001").getId();
        String page = html("/ai?sahe=FZL-001");
        assertThat(page).contains("Nümunə analiz — demo məlumat, real AI çağırışı deyil", "data-ai-state=\"SAMPLE\"",
                "Bu sahə üçün yalnız nümunə analiz var", "href=\"#numune\"");
        assertThat(count(page, "/saheler/" + fzl + "/peyk.jpg")).isEqualTo(1);
        // The sample never shows a model name or "real analysis" heading
        assertThat(page).doesNotContain("Real AI analizi", ">sample ·");
        assertThat(page).doesNotContainPattern("FZL-001 · [^<]*✓ AI"); // the picker marks only real analyses
        assertThat(html("/ai?sahe=fzl-001")).contains("data-ai-state=\"SAMPLE\""); // code is case-insensitive
    }

    @Test
    void realResultFailedAndRunningStates() throws Exception {
        Plot lcn = plot("LCN-001"), shs = plot("SHS-001"), agd = plot("AGD-001");
        save(lcn, "DONE", """
                {"summary":"Real analiz xülasəsi.","landCover":"Ot","terrain":"Düz","waterSigns":"Kanal","observations":[],
                 "risks":[{"title":"Şaxta","level":"HIGH","reason":"Qış soyuqdur"}],"suitableCrops":[{"code":"APPLE","name":"Alma","score":88,"reason":"Uyğun"}],
                 "notRecommended":[],"irrigationAdvice":"Damcı suvarma","recommendedChecks":[],"confidence":"MEDIUM","dataGaps":[]}""");
        save(shs, "FAILED", null);
        @SuppressWarnings("unchecked") Set<Long> running = (Set<Long>) ReflectionTestUtils.getField(aiService, "running");
        running.add(agd.getId());
        try {
            String real = html("/ai?sahe=LCN-001");
            assertThat(real).contains("data-ai-state=\"AVAILABLE\"", "Real AI analizi", "fake-model", "Real analiz xülasəsi.",
                    "Alma", "Damcı suvarma", "Pasportda nəticəyə bax", "/saheler/" + lcn.getId() + "#ai", "LCN-001 · " + lcn.getName());
            assertThat(real).contains("✓ AI"); // the option of the analysed plot
            assertThat(html("/ai?sahe=SHS-001")).contains("data-ai-state=\"FAILED\"", "Son analiz cəhdi alınmadı")
                    .doesNotContain("Real AI analizi", "fake error text");
            assertThat(html("/ai?sahe=AGD-001")).contains("data-ai-state=\"RUNNING\"", "Bu sahə üçün analiz hazırda gedir")
                    .doesNotContain("Pasportda AI ilə analiz et");
            assertThat(html("/ai?sahe=LCN-001&lang=en")).contains("An AI analysis of this plot is ready");
        } finally {
            running.remove(agd.getId());
        }
        assertThat(calls.get()).isZero();
    }

    @Test
    void callToActionFollowsLoginAndKey() throws Exception {
        long xcl = plot("XCL-001").getId();
        assertThat(html("/ai?sahe=XCL-001")).contains("data-ai-state=\"NONE\"", "Analiz üçün daxil olun", "href=\"/giris\"")
                .doesNotContain("Pasportda AI ilə analiz et");
        assertThat(html("/ai?sahe=XCL-001", INVESTOR)).contains("Pasportda AI ilə analiz et", "href=\"/saheler/" + xcl + "#ai\"")
                .doesNotContain("Analiz üçün daxil olun");
        configured.set(false);
        try {
            assertThat(html("/ai?sahe=XCL-001", INVESTOR)).contains("AI xidməti hazırda deaktivdir", "AI açarı konfiqurasiya edilməyib")
                    .doesNotContain("Pasportda AI ilə analiz et");
        } finally {
            configured.set(true);
        }
    }

    @Test
    void draftsAndUnknownCodesStayHidden() throws Exception {
        Plot draft = plot("KLB-002");
        draft.setListingStatus(ListingStatus.DRAFT);
        plots.save(draft);
        try {
            assertThat(html("/ai")).doesNotContain("KLB-002");
            String page = html("/ai?sahe=KLB-002");
            assertThat(page).contains("Sahə tapılmadı").doesNotContain("peyk.jpg", "KLB-002 ·");
            String odd = mvc.perform(get("/ai").param("sahe", "<i>zz</i>")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(odd).contains("Sahə tapılmadı").doesNotContain("<i>zz</i>"); // the raw value is never echoed
            // Staff do not get drafts here either (they use the passport)
            assertThat(html("/ai?sahe=KLB-002", user("admin@demo.az").roles("ADMIN"))).contains("Sahə tapılmadı");
        } finally {
            draft.setListingStatus(ListingStatus.PUBLISHED);
            plots.save(draft);
        }
    }

    @Test
    void onlyGetIsPublic() throws Exception {
        int s = mvc.perform(post("/ai").with(csrf())).andReturn().getResponse().getStatus();
        assertThat(s).isNotEqualTo(200); // anyRequest().authenticated() → login redirect
        mvc.perform(post("/saheler/" + plot("LCN-001").getId() + "/ai-analiz").with(csrf())
                        .accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection()); // running an analysis still needs a login
        assertThat(calls.get()).isZero();
    }

    void save(Plot plot, String status, String json) {
        AiAnalysis a = new AiAnalysis();
        a.setPlot(plot);
        a.setModel("fake-model");
        a.setCreatedAt(LocalDateTime.now());
        a.setStatus(status);
        a.setResultJson(json);
        a.setError("DONE".equals(status) ? null : "fake error text");
        analyses.save(a);
    }
}

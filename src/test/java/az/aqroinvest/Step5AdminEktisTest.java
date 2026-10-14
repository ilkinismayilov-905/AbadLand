package az.aqroinvest;

import az.aqroinvest.domain.*;
import az.aqroinvest.integration.MockEktisClient;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.ApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:step5;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.external-api-key=test-key"})
class Step5AdminEktisTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationRepository applications;
    @Autowired IntegrationLogRepository logs;
    @Autowired CompetitionRepository competitions;
    @Autowired PlotRepository plots;
    @Autowired ApplicationService service;
    @Autowired MockEktisClient mock;

    static final RequestPostProcessor COMMISSION = user("komissiya@demo.az").roles("COMMISSION");
    static final RequestPostProcessor OPERATOR = user("operator@demo.az").roles("OPERATOR");
    static final RequestPostProcessor ADMIN = user("admin@demo.az").roles("ADMIN");

    @AfterEach
    void resetMock() {
        mock.setFail(false);
    }

    Application underReview(String number) {
        Application a = applications.findByNumber(number).orElseThrow();
        if (a.getStatus() == ApplicationStatus.SUBMITTED) service.startReview(a.getId(), "operator@demo.az");
        return a;
    }

    IntegrationLog lastLog() {
        return logs.findAll().stream().max(Comparator.comparing(IntegrationLog::getId)).orElseThrow();
    }

    @Test
    void approveSendsToEktisAndDashboardChanges() throws Exception {
        mvc.perform(get("/idare").with(OPERATOR)).andExpect(content().string(containsString(">2</div><div class=\"text-sm text-gray-600\">Təsdiqlənmiş")));
        Application a = underReview("QAR-2026-000005"); // UNDER_REVIEW in seed
        long t0 = System.currentTimeMillis();
        mvc.perform(post("/idare/muracietler/" + a.getId() + "/qerar").param("decision", "APPROVED")
                .param("reason", "Taxılçılıq üzrə güclü biznes plan.").with(COMMISSION).with(csrf()));
        assertThat(System.currentTimeMillis() - t0).isLessThan(1000);
        IntegrationLog l = lastLog();
        assertThat(l.getStatus()).isEqualTo(IntegrationStatus.SUCCESS);
        assertThat(l.getEktisRef()).matches("EKTIS-INV-\\d{4}-\\d{6}");
        assertThat(l.getRequestJson()).contains("\"schemaVersion\":\"1.0\"", "\"competitionCode\":\"MUS-2026-04\"", "\"fin\":\"3NGR8P1\"");
        assertThat(applications.findById(a.getId()).orElseThrow().getEktisRef()).isEqualTo(l.getEktisRef());
        mvc.perform(get("/idare").with(OPERATOR)).andExpect(content().string(containsString(">3</div><div class=\"text-sm text-gray-600\">Təsdiqlənmiş")));
        // UI masks FİN in JSON
        mvc.perform(get("/idare/inteqrasiya").with(OPERATOR)).andExpect(content().string(allOf(
                containsString("Rejim: MOCK"), containsString(l.getEktisRef()), containsString("3NG****"), not(containsString("3NGR8P1")))));
    }

    @Test
    void failedThenRetryAfterSwitchingBack() throws Exception {
        Application a = underReview("QAR-2026-000002");
        mvc.perform(post("/idare/inteqrasiya/xeta-rejimi").with(OPERATOR).with(csrf())).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/idare/inteqrasiya/xeta-rejimi").with(ADMIN).with(csrf()));
        assertThat(mock.isFail()).isTrue();
        mvc.perform(post("/idare/muracietler/" + a.getId() + "/qerar").param("decision", "REJECTED")
                .param("reason", "Suvarma planı kifayət deyil.").with(COMMISSION).with(csrf()));
        IntegrationLog failed = lastLog();
        assertThat(failed.getStatus()).isEqualTo(IntegrationStatus.FAILED);
        assertThat(applications.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(ApplicationStatus.DECIDED); // decision stands
        mvc.perform(get("/idare/inteqrasiya").with(ADMIN)).andExpect(content().string(containsString("Yenidən göndər")));

        mvc.perform(post("/idare/inteqrasiya/" + failed.getId() + "/yeniden").with(OPERATOR).with(csrf())).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/idare/inteqrasiya/xeta-rejimi").with(ADMIN).with(csrf()));
        mvc.perform(post("/idare/inteqrasiya/" + failed.getId() + "/yeniden").with(ADMIN).with(csrf())).andExpect(flash().attributeExists("success"));
        assertThat(lastLog().getStatus()).isEqualTo(IntegrationStatus.SUCCESS);
        assertThat(applications.findById(a.getId()).orElseThrow().getEktisRef()).isNotNull();
        mvc.perform(post("/idare/inteqrasiya/" + failed.getId() + "/yeniden").with(ADMIN).with(csrf())).andExpect(flash().attributeExists("error"));
    }

    @Test
    void competitionPublicOnlyAfterApproval() throws Exception {
        long klb = plots.findByCode("KLB-001").orElseThrow().getId();
        long fzl = plots.findByCode("FZL-001").orElseThrow().getId(); // already in a PUBLISHED competition
        mvc.perform(post("/idare/musabiqeler").with(OPERATOR).with(csrf()).param("title", "Test").param("requirements", "x")
                        .param("minInvestment", "100000").param("deadline", LocalDate.now().minusDays(1).toString()).param("plotIds", "" + fzl))
                .andExpect(status().isOk()).andExpect(content().string(allOf(containsString("gələcəkdə"), containsString("artıq dərc edilmiş"))));
        mvc.perform(post("/idare/musabiqeler").with(OPERATOR).with(csrf()).param("title", "Kəlbəcər otlaqları 2027")
                        .param("requirements", "Heyvandarlıq təcrübəsi").param("minInvestment", "150000")
                        .param("deadline", LocalDate.now().plusDays(20).toString()).param("plotIds", "" + klb))
                .andExpect(flash().attribute("success", containsString("MUS-")));
        Competition c = competitions.findAll().stream().filter(x -> x.getTitle().equals("Kəlbəcər otlaqları 2027")).findFirst().orElseThrow();
        assertThat(c.getStatus()).isEqualTo(CompetitionStatus.PENDING_APPROVAL);
        assertThat(c.getCode()).isEqualTo("MUS-" + LocalDate.now().getYear() + "-06");
        mvc.perform(get("/musabiqeler")).andExpect(content().string(not(containsString("Kəlbəcər otlaqları 2027"))));
        mvc.perform(post("/idare/musabiqeler/" + c.getId() + "/tesdiq").with(OPERATOR).with(csrf())).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/idare/musabiqeler/" + c.getId() + "/tesdiq").with(COMMISSION).with(csrf())).andExpect(flash().attributeExists("success"));
        mvc.perform(get("/musabiqeler")).andExpect(content().string(containsString("Kəlbəcər otlaqları 2027")));
        // reject needs a comment
        Competition pending = competitions.findAll().stream().filter(x -> x.getCode().equals("MUS-2026-05")).findFirst().orElseThrow();
        mvc.perform(post("/idare/musabiqeler/" + pending.getId() + "/redd").with(COMMISSION).with(csrf())).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/idare/musabiqeler/" + pending.getId() + "/redd").param("comment", "Sahələr hazır deyil").with(COMMISSION).with(csrf()))
                .andExpect(flash().attributeExists("success"));
    }

    @Test
    void auditApiAndSwagger() throws Exception {
        mvc.perform(get("/idare/audit").with(OPERATOR)).andExpect(status().isForbidden());
        mvc.perform(get("/idare/audit").with(COMMISSION)).andExpect(status().isOk()).andExpect(content().string(containsString("STATUS_CHANGE")));
        mvc.perform(get("/api/v1/plots")).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.code=='FZL-001')].openCompetition").value(hasItem("MUS-2026-03")));
        mvc.perform(get("/api/v1/plots/FZL-001")).andExpect(jsonPath("$.suitability[0].cropName").value("Nar"));
        mvc.perform(get("/api/v1/competitions")).andExpect(jsonPath("$[*].code", hasItem("MUS-2026-03")));
        mvc.perform(get("/api/v1/applications/QAR-2026-000007/status")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/applications/QAR-2026-000007/status").with(user("rashad@demo.az").roles("INVESTOR")))
                .andExpect(jsonPath("$.status").value("DECIDED"));
        mvc.perform(get("/api/v1/applications/QAR-2026-000007/status").with(user("elvin@demo.az").roles("INVESTOR"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/external/decisions")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/external/decisions").header("X-API-Key", "test-key")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].applicationNumber", hasItem("QAR-2026-000007")));
        mvc.perform(get("/v3/api-docs/v1")).andExpect(status().isOk()).andExpect(content().string(allOf(
                containsString("/api/v1/plots"), containsString("X-API-Key"), not(containsString("/kabinet")))));
    }
}

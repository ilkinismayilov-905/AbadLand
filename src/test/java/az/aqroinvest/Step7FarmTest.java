package az.aqroinvest;

import az.aqroinvest.domain.*;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.ApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Demo path step 10–11: approved → crop plan → calendar → done → workers → report → operator monitoring. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:step7;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.upload-dir=target/test-uploads7", "app.external.enabled=false"})
class Step7FarmTest {

    @Autowired MockMvc mvc;
    @Autowired ApplicationService applications;
    @Autowired PlotRepository plots;
    @Autowired FarmProjectRepository farms;
    @Autowired CalendarTaskRepository tasks;
    @Autowired NotificationRepository notifications;

    static final RequestPostProcessor ELVIN = user("elvin@demo.az").roles("INVESTOR");
    static final RequestPostProcessor OPERATOR = user("operator@demo.az").roles("OPERATOR");

    @Test
    void farmManagementFlow() throws Exception {
        // approve an application for Elvin on FZL-001 (Step 4 flow, via the service)
        Plot fzl = plots.findByCode("FZL-001").orElseThrow();
        var app = applications.submit("elvin@demo.az", new ApplicationService.NewApplication(fzl.getId(), "HAZELNUT", 410_000L, 24, 36, "Fındıq"),
                new MockMultipartFile("f", "plan.pdf", "application/pdf", "%PDF-1.4".getBytes()), null);
        applications.startReview(app.getId(), "operator@demo.az");
        applications.decide(app.getId(), "komissiya@demo.az", Decision.APPROVED, "Biznes plan əsaslandırılıb.");
        FarmProject farm = farms.findByApplication(app).orElseThrow();
        assertThat(notifications.findByUserEmailIgnoreCaseOrderByAtDescIdDesc("elvin@demo.az"))
                .anyMatch(n -> new az.aqroinvest.web.I18n(az.aqroinvest.web.I18n.bundles()).render(n.getMessage()).startsWith("Təsərrüfat layihəniz yaradıldı") && n.getLink().equals("/kabinet/teserrufat/" + farm.getId()));
        String page = "/kabinet/teserrufat/" + farm.getId();

        mvc.perform(get("/kabinet").with(ELVIN)).andExpect(content().string(containsString(page)));
        mvc.perform(get(page).with(ELVIN)).andExpect(status().isOk()).andExpect(content().string(allOf(
                containsString("Öhdəliklər"), containsString("Plan üzrə"), containsString("Əkin planı hələ yoxdur"),
                containsString("<option value=\"HAZELNUT\" selected=\"selected\">Fındıq</option>"))));
        mvc.perform(get(page).with(user("rashad@demo.az").roles("INVESTOR"))).andExpect(status().isNotFound());

        // crop plan: too large rejected, Fındıq 40 ha → calendar tasks
        mvc.perform(post(page + "/plan").param("crop", "HAZELNUT").param("areaHa", "50").param("seasonYear", "" + LocalDate.now().getYear())
                .with(ELVIN).with(csrf())).andExpect(flash().attribute("error", containsString("aşa bilməz")));
        mvc.perform(post(page + "/plan").param("crop", "HAZELNUT").param("areaHa", "40").param("seasonYear", "" + LocalDate.now().getYear())
                .with(ELVIN).with(csrf())).andExpect(flash().attributeExists("success"));
        var created = tasks.findByProjectOrderByPlannedStartAscIdAsc(farm);
        assertThat(created).hasSize(5).extracting(CalendarTask::getStage).contains(CalendarStage.SOIL_PREP, CalendarStage.HARVEST);

        // mark soil prep done; future date rejected
        CalendarTask soil = created.stream().filter(t -> t.getStage() == CalendarStage.SOIL_PREP).findFirst().orElseThrow();
        mvc.perform(post(page + "/tapsiriq/" + soil.getId()).param("date", LocalDate.now().plusDays(3).toString()).with(ELVIN).with(csrf()))
                .andExpect(flash().attribute("error", containsString("gələcəkdə")));
        mvc.perform(post(page + "/tapsiriq/" + soil.getId()).param("date", LocalDate.now().toString()).param("note", "Şumlandı")
                .with(ELVIN).with(csrf())).andExpect(flash().attributeExists("success"));
        assertThat(tasks.findById(soil.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.DONE);

        // workers + report with a photo
        mvc.perform(post(page + "/isci").param("role", "WORKER").param("count", "10").param("local", "true").with(ELVIN).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post(page + "/isci").param("role", "AGRONOMIST").param("count", "2").with(ELVIN).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(multipart(page + "/hesabat").file(new MockMultipartFile("photos", "sahe.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}))
                        .param("period", "2026-Q4").param("investedToDate", "123000").param("note", "Torpaq hazırlandı").with(ELVIN).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(get(page).with(ELVIN)).andExpect(content().string(allOf(containsString("12 / 24"), containsString("✓"),
                containsString("Şumlandı"), containsString("2026-Q4"), containsString("sahe.jpg"))));

        // operator monitoring: percentages, delays, risk; review report
        mvc.perform(get("/idare/monitorinq").with(OPERATOR)).andExpect(status().isOk()).andExpect(content().string(allOf(
                containsString(app.getNumber()), containsString("12 / 24 (50%)"), containsString("30%"), containsString("83%"),
                containsString("QAR-2025-000031"), containsString("Risk var"), containsString("Plan üzrə"))));
        mvc.perform(get("/idare/monitorinq/" + farm.getId()).with(OPERATOR)).andExpect(content().string(allOf(
                containsString("Qəbul et"), containsString("Düzəliş istə"), containsString("peyk.jpg"), not(containsString("Hesabatı göndər")))));
        String detail = mvc.perform(get("/idare/monitorinq/" + farm.getId()).with(OPERATOR)).andReturn().getResponse().getContentAsString();
        String rid = detail.replaceAll("(?s).*/idare/monitorinq/" + farm.getId() + "/hesabat/(\\d+).*", "$1");
        mvc.perform(post("/idare/monitorinq/" + farm.getId() + "/hesabat/" + rid).param("accept", "false").with(OPERATOR).with(csrf()))
                .andExpect(flash().attribute("error", containsString("düzəldilməli")));
        mvc.perform(post("/idare/monitorinq/" + farm.getId() + "/hesabat/" + rid).param("accept", "true").with(OPERATOR).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post("/idare/monitorinq/" + farm.getId() + "/hesabat/" + rid).param("accept", "true").with(OPERATOR).with(csrf()))
                .andExpect(flash().attribute("error", containsString("artıq")));

        // seeded AT_RISK project: red delayed task; dashboard KPIs
        FarmProject tural = farms.findAll().stream().filter(f -> "QAR-2025-000031".equals(f.sourceLabel())).findFirst().orElseThrow();
        mvc.perform(get("/idare/monitorinq/" + tural.getId()).with(OPERATOR)).andExpect(content().string(allOf(
                containsString("bg-red-500"), containsString("Gecikir"), containsString("Risk var"))));
        mvc.perform(get("/idare").with(OPERATOR)).andExpect(content().string(allOf(
                containsString("Aktiv təsərrüfat layihəsi"), containsString("29 / 61"), containsString("Gecikən əkin tapşırıqları"))));
    }
}

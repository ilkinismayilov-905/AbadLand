package az.aqroinvest;

import az.aqroinvest.domain.Application;
import az.aqroinvest.domain.ApplicationStatus;
import az.aqroinvest.domain.Decision;
import az.aqroinvest.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Demo path steps 6–9: submit → review → info request → reply with PDF → commission approves. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:step4;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.upload-dir=target/test-uploads"})
class Step4ApplicationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired PlotRepository plots;
    @Autowired ApplicationRepository applications;
    @Autowired NotificationRepository notifications;
    @Autowired StatusHistoryRepository history;
    @Autowired DocumentRepository documents;
    @Autowired FarmProjectRepository farms;
    @Autowired AuditLogRepository audit;

    static final RequestPostProcessor ELVIN = user("elvin@demo.az").roles("INVESTOR");
    static final RequestPostProcessor RASHAD = user("rashad@demo.az").roles("INVESTOR");
    static final RequestPostProcessor OPERATOR = user("operator@demo.az").roles("OPERATOR");
    static final RequestPostProcessor COMMISSION = user("komissiya@demo.az").roles("COMMISSION");

    static MockMultipartFile pdf(String field) {
        return new MockMultipartFile(field, "biznes-plan.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
    }

    @Test
    void fullDemoLoop() throws Exception {
        long plotId = plots.findByCode("FZL-001").orElseThrow().getId();
        mvc.perform(get("/kabinet/muraciet/yeni").param("plotId", "" + plotId).with(ELVIN))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Minimum 200 000 AZN")));

        // all errors at once: below minimum, no file, no consent; fake PDF rejected by magic bytes
        mvc.perform(multipart("/kabinet/muraciet").file(new MockMultipartFile("otherFile", "x.pdf", "application/pdf", "hello".getBytes()))
                        .param("plotId", "" + plotId).param("plannedCrop", "HAZELNUT").param("investmentAzn", "1000")
                        .param("jobs", "20").param("months", "36").param("businessPlan", "Plan").with(ELVIN).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("minimumdan az"), containsString("Biznes plan faylı tələb olunur"),
                        containsString("təsdiqləyin"), containsString("PDF formatına uyğun deyil"))));
        assertThat(applications.count()).isEqualTo(8);

        // submit
        String location = mvc.perform(multipart("/kabinet/muraciet").file(pdf("businessPlanFile"))
                        .param("plotId", "" + plotId).param("plannedCrop", "HAZELNUT").param("investmentAzn", "410000")
                        .param("jobs", "24").param("months", "36").param("businessPlan", "Fındıq bağı, damcı suvarma.")
                        .param("consent", "true").with(ELVIN).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("created", "QAR-2026-000008"))
                .andReturn().getResponse().getRedirectedUrl();
        Application app = applications.findAll().stream().filter(a -> a.getNumber().equals("QAR-2026-000008")).findFirst().orElseThrow();
        assertThat(location).isEqualTo("/kabinet/muraciet/" + app.getId());
        long id = app.getId();
        mvc.perform(get(location).with(ELVIN)).andExpect(status().isOk()).andExpect(content().string(containsString("QAR-2026-000008")));

        // ownership + roles
        mvc.perform(get(location).with(RASHAD)).andExpect(status().isNotFound());
        mvc.perform(get("/idare/muracietler").with(ELVIN)).andExpect(status().isForbidden());
        mvc.perform(get("/idare/muracietler").with(OPERATOR)).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("QAR-2026-000008"), containsString("5EL****"), not(containsString("5ELV1N7")))));

        // operator: start review, request info; commission cannot decide meanwhile
        mvc.perform(post("/idare/muracietler/" + id + "/baxis").with(OPERATOR).with(csrf())).andExpect(flash().attributeExists("success"));
        mvc.perform(post("/idare/muracietler/" + id + "/baxis").with(OPERATOR).with(csrf())).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/idare/muracietler/" + id + "/melumat").param("message", "Torpaq analizini əlavə edin.").with(OPERATOR).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        mvc.perform(post("/idare/muracietler/" + id + "/qerar").param("decision", "APPROVED").param("reason", "Kifayət qədər əsas var")
                .with(COMMISSION).with(csrf())).andExpect(flash().attributeExists("error"));
        assertThat(statusOf(id)).isEqualTo(ApplicationStatus.INFO_REQUESTED);

        // Elvin: badge, notifications page, reply with PDF
        assertThat(notifications.countByUserEmailIgnoreCaseAndReadFalse("elvin@demo.az")).isEqualTo(3);
        mvc.perform(get("/kabinet").with(ELVIN)).andExpect(content().string(containsString("bg-red-600 text-white text-xs rounded-full px-1.5\">3<")));
        mvc.perform(get("/bildirisler").with(ELVIN)).andExpect(content().string(allOf(containsString("Torpaq analizini"), containsString(">yeni<"))));
        assertThat(notifications.countByUserEmailIgnoreCaseAndReadFalse("elvin@demo.az")).isZero();
        mvc.perform(get(location).with(ELVIN)).andExpect(content().string(containsString("Cavabı göndər")));
        mvc.perform(multipart(location + "/cavab").file(new MockMultipartFile("file", "torpaq.pdf", "application/pdf", "%PDF-1.7 soil".getBytes()))
                        .param("response", "Torpaq analizi əlavə olundu.").param("fileType", "SOIL_ANALYSIS").with(ELVIN).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(statusOf(id)).isEqualTo(ApplicationStatus.UNDER_REVIEW);
        assertThat(documents.findByApplicationOrderByUploadedAtAsc(applications.findById(id).orElseThrow())).hasSize(2);

        // commission: short reason rejected, then approve
        mvc.perform(post("/idare/muracietler/" + id + "/qerar").param("decision", "APPROVED").param("reason", "qısa")
                .with(COMMISSION).with(csrf())).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/idare/muracietler/" + id + "/qerar").param("decision", "APPROVED")
                        .param("reason", "Biznes plan əsaslandırılıb, torpaq analizi müsbətdir.").with(COMMISSION).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        Application decided = applications.findById(id).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(ApplicationStatus.DECIDED);
        assertThat(decided.getDecision()).isEqualTo(Decision.APPROVED);
        assertThat(farms.findAll()).anyMatch(f -> f.getApplication().getId().equals(id) && f.getCommittedJobs() == 24);
        assertThat(history.findByApplicationOrderByAtAscIdAsc(decided)).hasSize(5);
        assertThat(audit.findAll()).filteredOn(a -> "QAR-2026-000008".equals(a.getEntityId())).hasSize(5);

        mvc.perform(get(location).with(ELVIN)).andExpect(content().string(allOf(
                containsString("Qərar verilib: Müsbət"), containsString("torpaq analizi müsbətdir"))));

        // downloads: owner and staff yes, other investor 404
        long docId = documents.findByApplicationOrderByUploadedAtAsc(decided).get(0).getId();
        mvc.perform(get("/fayl/" + docId).with(ELVIN)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(get("/fayl/" + docId).with(OPERATOR)).andExpect(status().isOk());
        mvc.perform(get("/fayl/" + docId).with(RASHAD)).andExpect(status().isNotFound());
    }

    ApplicationStatus statusOf(long id) {
        return applications.findById(id).orElseThrow().getStatus();
    }
}

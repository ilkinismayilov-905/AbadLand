package az.aqroinvest;

import az.aqroinvest.ai.AiAnalysisService;
import az.aqroinvest.ai.AiClient;
import az.aqroinvest.domain.*;
import az.aqroinvest.farm.FarmService;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The SPEC demo path, steps 1–11, through the services (AI client mocked, no network). */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:demopath;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.external.enabled=false", "app.upload-dir=target/test-uploads-demo"})
class DemoPathTest {

    @TestConfiguration
    static class FakeAi {
        @Bean
        @Primary
        AiClient fakeAi() {
            return new AiClient() {
                public boolean isConfigured() { return true; }
                public String model() { return "fake"; }
                public String analyze(byte[] jpeg, String text) {
                    return """
                            {"summary":"Dağ vadisi","landCover":"Ot","terrain":"Yamac","waterSigns":"Çay","observations":[],
                             "risks":[{"title":"Şaxta","level":"MEDIUM","reason":"Qış soyuqdur"}],
                             "suitableCrops":[{"code":"WALNUT","name":"Qoz","score":88,"reason":"Hündürlük"}],
                             "notRecommended":[],"irrigationAdvice":"Damcı","recommendedChecks":[],"confidence":"MEDIUM","dataGaps":[]}""";
                }
            };
        }
    }

    @Autowired MatchingService matching;
    @Autowired PlotService plotService;
    @Autowired SuitabilityService suitability;
    @Autowired RoiService roi;
    @Autowired AiAnalysisService ai;
    @Autowired ApplicationService applications;
    @Autowired FarmService farms;
    @Autowired PlotRepository plots;
    @Autowired CropRepository crops;
    @Autowired PlotClimateRepository climates;
    @Autowired IntegrationLogRepository integrationLogs;
    @Autowired FarmProjectRepository farmRepo;
    @Autowired CalendarTaskRepository tasks;
    @Autowired NotificationRepository notifications;

    @Test
    void demoPath() {
        // 1. quiz → FZL-001 first
        var matches = matching.match(new MatchingService.Answers(ActivityType.HORTICULTURE, 500_000L, "20-50", "Füzuli", "3-10"));
        assertThat(matches.get(0).plot().getCode()).isEqualTo("FZL-001");
        assertThat(matches.get(0).score()).isGreaterThanOrEqualTo(90);

        // 2. fruit filter Nar → only pomegranate-suitable plots (FZL-001 in, livestock out)
        var nar = plotService.search(new PlotService.PlotFilter(null, null, null, null, null, null, "POMEGRANATE"));
        assertThat(nar).extracting(c -> c.plot().getCode()).contains("FZL-001").doesNotContain("KLB-001");
        assertThat(nar).allMatch(c -> c.fruitScore() >= 60);

        // 3. FZL-001 passport ROI + prediction for Fındıq
        Plot fzl = plots.findByCode("FZL-001").orElseThrow();
        var r = roi.calculate(fzl, crops.findByCode("HAZELNUT").orElseThrow(), climates.findByPlot(fzl).orElse(null), 78);
        assertThat(r.scenarios()).hasSize(3);
        assertThat(r.risks()).hasSize(3);
        assertThat(r.payback()).isNotNull();

        // 4. LCN-001 AI analysis → AI suitability
        Plot lcn = plots.findByCode("LCN-001").orElseThrow();
        assertThat(ai.analyze(lcn, "elvin@demo.az", false).getStatus()).isEqualTo("DONE");
        assertThat(suitability.forPlot(lcn)).anyMatch(s -> s.source() == SuitabilitySource.AI && s.crop().getCode().equals("WALNUT"));

        // 6. apply → number
        var app = applications.submit("elvin@demo.az", new ApplicationService.NewApplication(fzl.getId(), "HAZELNUT", 410_000L, 24, 36, "Fındıq bağı"),
                new MockMultipartFile("f", "plan.pdf", "application/pdf", "%PDF-1.4".getBytes()), null);
        assertThat(app.getNumber()).matches("QAR-\\d{4}-\\d{6}");

        // 7–8. operator review → info request → Elvin responds with a PDF
        applications.startReview(app.getId(), "operator@demo.az");
        applications.requestInfo(app.getId(), "operator@demo.az", "Torpaq analizini əlavə edin");
        assertThat(notifications.countByUserEmailIgnoreCaseAndReadFalse("elvin@demo.az")).isGreaterThanOrEqualTo(3);
        applications.respond(app.getId(), "elvin@demo.az", "Əlavə olundu",
                new MockMultipartFile("f", "torpaq.pdf", "application/pdf", "%PDF-1.7".getBytes()), DocType.SOIL_ANALYSIS);

        // 9. commission approves → EKTİS SUCCESS → FarmProject
        applications.decide(app.getId(), "komissiya@demo.az", Decision.APPROVED, "Biznes plan əsaslandırılıb, torpaq uyğundur.");
        var decided = applications.get(app.getId());
        assertThat(decided.getStatus()).isEqualTo(ApplicationStatus.DECIDED);
        assertThat(integrationLogs.findAll()).anyMatch(l -> l.getApplicationNumber().equals(app.getNumber())
                && l.getStatus() == IntegrationStatus.SUCCESS && l.getEktisRef().equals(decided.getEktisRef()));
        FarmProject farm = farmRepo.findByApplication(decided).orElseThrow();

        // 10. crop plan Fındıq 40 ha → calendar; workers
        farms.addCropPlan(farm.getId(), "elvin@demo.az", "HAZELNUT", 40.0, LocalDate.now().getYear());
        assertThat(tasks.findByProjectOrderByPlannedStartAscIdAsc(farm)).hasSize(5);
        farms.addWorkers(farm.getId(), "elvin@demo.az", WorkRole.WORKER, 12, true);

        // 11. operator monitoring sees promised vs actual jobs and the seeded delayed project
        var all = farms.all();
        var mine = all.stream().filter(v -> v.project().getId().equals(farm.getId())).findFirst().orElseThrow();
        assertThat(mine.progress().workers()).isEqualTo(12);
        assertThat(mine.progress().jobsPct()).isEqualTo(50);
        assertThat(all).anyMatch(v -> v.progress().risk() == FarmStatus.AT_RISK && v.progress().delayedTasks() > 0);
    }
}

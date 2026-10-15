package az.aqroinvest;

import az.aqroinvest.domain.*;
import az.aqroinvest.listing.Geo;
import az.aqroinvest.repo.*;
import az.aqroinvest.service.CompetitionService;
import az.aqroinvest.service.PlotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Staff plot listings: draft → photos → admin publishes into a pending competition → commission approves; validation; visibility. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:listing;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL",
        "app.external.enabled=false", "app.upload-dir=target/test-uploads-listing"})
class PlotListingTest {

    static final RequestPostProcessor OPERATOR = user("operator@demo.az").roles("OPERATOR");
    static final RequestPostProcessor ADMIN = user("admin@demo.az").roles("ADMIN");
    /** ~25 ha square in Füzuli. */
    static final String POLY = "{\"type\":\"Polygon\",\"coordinates\":[[[47.10,39.60],[47.1058,39.60],[47.1058,39.6045],[47.10,39.6045],[47.10,39.60]]]}";

    @Autowired MockMvc mvc;
    @Autowired PlotRepository plots;
    @Autowired CompetitionRepository competitions;
    @Autowired CompetitionService competitionService;
    @Autowired PlotService plotService;
    @Autowired ObjectMapper json;

    static MockMultipartFile jpg(String name) {
        return new MockMultipartFile("photos", name, "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01});
    }

    Plot newest() {
        return plots.findAll().stream().filter(p -> p.getListingStatus() != ListingStatus.PUBLISHED || p.getCreatedBy() != null)
                .max(java.util.Comparator.comparing(Plot::getId)).orElseThrow();
    }

    Long draft() throws Exception {
        mvc.perform(post("/idare/elan/yeni").with(OPERATOR).with(csrf()).param("district", "Füzuli").param("village", "Horadiz").param("geoJson", POLY))
                .andExpect(status().is3xxRedirection());
        Long id = newest().getId();
        mvc.perform(post("/idare/elan/" + id + "/addim/2").with(OPERATOR).with(csrf()).param("title", "Horadiz nar bağı sahəsi")
                .param("category", "HORTICULTURE").param("description", "Suvarılan düz sahə, nar bağı üçün.").param("crops", "POMEGRANATE"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/idare/elan/" + id + "/addim/3").with(OPERATOR).with(csrf()).param("demining", "CLEARED").param("roadKm", "3")
                .param("leaseYears", "25").param("feePerHa", "60").param("irrigation", "true")).andExpect(status().is3xxRedirection());
        return id;
    }

    @Test
    void draftPhotosPublishIntoCompetitionAndApprove() throws Exception {
        Long id = draft();
        Plot p = plots.findById(id).orElseThrow();
        assertThat(p.getCode()).matches("FZL-\\d{3}");
        assertThat(p.getAreaHa()).isBetween(24.0, 27.0);
        assertThat(p.getListingStatus()).isEqualTo(ListingStatus.DRAFT);

        // drafts are invisible to the public
        mvc.perform(get("/saheler/" + id)).andExpect(status().isNotFound());
        assertThat(plotService.search(PlotService.PlotFilter.none())).noneMatch(c -> c.plot().getId().equals(id));

        // < 3 photos → cannot publish (all problems listed)
        mvc.perform(multipart("/idare/elan/" + id + "/fayllar").file(jpg("a.jpg")).file(jpg("b.jpg")).with(OPERATOR).with(csrf()));
        mvc.perform(post("/idare/elan/" + id + "/derc").with(ADMIN).with(csrf())).andExpect(flash().attributeExists("submitErrors"));
        mvc.perform(multipart("/idare/elan/" + id + "/fayllar").file(jpg("c.jpg")).with(OPERATOR).with(csrf()));

        // operator may not publish
        mvc.perform(post("/idare/elan/" + id + "/derc").with(OPERATOR).with(csrf())).andExpect(flash().attribute("error", containsString("admin")));

        Competition pending = competitions.findAll().stream().filter(c -> c.getStatus() == CompetitionStatus.PENDING_APPROVAL).findFirst().orElseThrow();
        mvc.perform(post("/idare/elan/" + id + "/derc").with(ADMIN).with(csrf()).param("after", "COMPETITION").param("competitionId", "" + pending.getId()))
                .andExpect(redirectedUrl("/idare/musabiqeler"));
        p = plots.findById(id).orElseThrow();
        assertThat(p.getListingStatus()).isEqualTo(ListingStatus.PUBLISHED);
        assertThat(p.isRecent()).isTrue();
        assertThat(plotService.search(PlotService.PlotFilter.none())).anyMatch(c -> c.plot().getId().equals(id));
        mvc.perform(get("/saheler/" + id)).andExpect(status().isOk()).andExpect(content().string(containsString("/foto/")));
        mvc.perform(get("/saheler")).andExpect(content().string(containsString("Horadiz nar bağı sahəsi")));

        // existing commission approval works; then the plot cannot be unpublished
        competitionService.approve(pending.getId(), "komissiya@demo.az");
        assertThat(plotService.openCompetitionByPlot().get(id).getId()).isEqualTo(pending.getId()); // plot now in a published competition
        mvc.perform(post("/idare/elan/" + id + "/dercden-cixar").with(ADMIN).with(csrf())).andExpect(flash().attributeExists("error"));
        assertThat(plots.findById(id).orElseThrow().isPublished()).isTrue();
    }

    @Test
    void invalidAreaRoadAndPolygonAreRejected() throws Exception {
        Long id = draft();
        mvc.perform(multipart("/idare/elan/" + id + "/addim/3").with(OPERATOR).with(csrf()).param("roadKm", "250").param("leaseYears", "60"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("0–200 km"))).andExpect(content().string(containsString("1–49")));
        mvc.perform(post("/idare/elan/" + id + "/addim/1").with(OPERATOR).with(csrf()).param("areaHa", "6000"))
                .andExpect(content().string(containsString("0,5–5 000 ha")));
        String outside = "{\"type\":\"Polygon\",\"coordinates\":[[[30,50],[30.01,50],[30.01,50.01],[30,50]]]}";
        mvc.perform(post("/idare/elan/" + id + "/addim/1").with(OPERATOR).with(csrf()).param("geoJson", outside))
                .andExpect(content().string(containsString("Azərbaycandan kənardadır")));
        mvc.perform(post("/idare/elan/" + id + "/addim/2").with(OPERATOR).with(csrf()).param("title", "Test"))
                .andExpect(content().string(containsString("10–120")));
        Plot p = plots.findById(id).orElseThrow();
        assertThat(p.getRoadKm()).isEqualTo(3);
        assertThat(p.getAreaHa()).isLessThan(30);

        assertThatThrownBy(() -> Geo.parse("{\"type\":\"Point\"}", json)).hasMessage("listing.geo.notPolygon");
        // investors and commission cannot open the wizard
        mvc.perform(get("/idare/elan/yeni").with(user("elvin@demo.az").roles("INVESTOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/idare/elanlar").with(user("komissiya@demo.az").roles("COMMISSION"))).andExpect(status().isForbidden());
        mvc.perform(get("/idare/elanlar").with(ADMIN)).andExpect(status().isOk()).andExpect(content().string(containsString("Aqrar payı")));
    }
}

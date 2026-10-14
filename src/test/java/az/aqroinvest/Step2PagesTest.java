package az.aqroinvest;

import az.aqroinvest.repo.PlotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Step2PagesTest {

    @Autowired MockMvc mvc;
    @Autowired PlotRepository plots;

    long id(String code) {
        return plots.findByCode(code).orElseThrow().getId();
    }

    @Test
    void fruitFilterNar() throws Exception {
        mvc.perform(get("/saheler").param("fruit", "POMEGRANATE").param("region", "").param("minHa", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("FZL-001")))
                .andExpect(content().string(not(containsString("KLB-001"))));
    }

    @Test
    void passportShowsChipsRoiScenariosRisks() throws Exception {
        mvc.perform(get("/saheler/" + id("FZL-001")))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("Nar 85%"), containsString("Fındıq 78%"),
                        containsString("382 500 AZN"), containsString("Pessimist"), containsString("Optimist"),
                        containsString("Şaxta"), containsString("Quraqlıq"), containsString("Su çatışmazlığı"),
                        containsString("Sınaq dəyəri"))));
        mvc.perform(get("/saheler/" + id("FZL-001")).param("crop", "POMEGRANATE").param("score", "91"))
                .andExpect(content().string(allOf(containsString("425 000 AZN"), containsString("91/100"))));
    }

    @Test
    void mapCompareAndCompetitions() throws Exception {
        mvc.perform(get("/xerite").param("fruit", "POMEGRANATE")).andExpect(status().isOk())
                .andExpect(content().string(containsString("FZL-001")));
        mvc.perform(get("/muqayise").param("ids", id("FZL-001") + "," + id("FZL-002"))).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("FZL-001"), containsString("FZL-002"), containsString("bg-green-50"))));
        mvc.perform(get("/musabiqeler")).andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("MUS-2026-03"), not(containsString("MUS-2026-05")))));
        mvc.perform(get("/saheler/999999")).andExpect(status().isNotFound());
    }

    @Test
    void viewer3dDataOnPassportAndMapOnly() throws Exception { // Thymeleaf JS inlining escapes non-ASCII (\\u00FC …)
        String three = "three.js/r128/three.min.js";
        mvc.perform(get("/saheler/" + id("FZL-001"))).andExpect(content().string(allOf(containsString(three), containsString("/js/plot3d.js"),
                containsString("F\\u00FCzuli \\u00B7 42,5 ha"), containsString("/kabinet/muraciet/yeni?plotId=" + id("FZL-001")))));
        mvc.perform(get("/xerite")).andExpect(content().string(allOf(containsString(three), containsString("C\\u0259bray\\u0131l \\u00B7 70 ha"))));
        mvc.perform(get("/saheler")).andExpect(content().string(not(containsString(three))));
        // staff get no "Müraciət et"; a plot without an open competition neither
        mvc.perform(get("/saheler/" + id("FZL-001")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                .user("operator@demo.az").roles("OPERATOR"))).andExpect(content().string(not(containsString("muraciet/yeni?plotId="))));
        mvc.perform(get("/saheler/" + id("CBR-002"))).andExpect(content().string(allOf(containsString("\"applyUrl\":null"), not(containsString("muraciet/yeni")))));
    }
}

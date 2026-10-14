package az.aqroinvest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Step3QuizTest {

    @Autowired MockMvc mvc;

    @Test
    void demoAnswersPutFzl001First() throws Exception {
        String html = mvc.perform(get("/uygunluq").param("activity", "HORTICULTURE").param("budget", "500000")
                        .param("size", "20-50").param("district", "Füzuli").param("experience", "3-10"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int first = html.indexOf("#1 · ");
        assertThat(html.substring(first, first + 40)).contains("FZL-001");
        assertThat(html).contains("100%", "✓ Büdcənizə sığır (təxmini 382 500 AZN)", "✓ Suvarma var",
                "✓ Minalardan tam təmizlənib", "Bal necə hesablanır?", "score=100", "✗ Suvarma yoxdur", "✗ Yola 12 km məsafə");
        assertThat(html).doesNotContain("KLB-001"); // not in a published competition
    }

    @Test
    void quizAndValidation() throws Exception {
        mvc.perform(get("/uygunluq")).andExpect(status().isOk()).andExpect(content().string(containsString("Nəticələri göstər")));
        mvc.perform(get("/uygunluq").param("activity", "HORTICULTURE"))
                .andExpect(content().string(containsString("bütün 5 suala")));
        mvc.perform(get("/uygunluq").param("activity", "HORTICULTURE").param("budget", "500000")
                        .param("size", "bad").param("district", "").param("experience", "0"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Cavablar düzgün deyil")));
        mvc.perform(get("/uygunluq").param("activity", "HORTICULTURE").param("budget", "500000")
                        .param("size", "20-50").param("district", "").param("experience", "0").param("edit", "1"))
                .andExpect(content().string(not(containsString("Sizə uyğun sahələr"))));
    }

    @Test
    void budgetLabelsAndCustomAnswersOnResults() throws Exception {
        mvc.perform(get("/uygunluq")).andExpect(content().string(org.hamcrest.Matchers.allOf(
                containsString("100\u00a0000 AZN"), containsString("2\u00a0000\u00a0000+ AZN"), containsString("Digər məbləğ"),
                containsString("1–5 ha"), containsString("Özüm yazım"), containsString("Fəaliyyət növünü yazın"))));
        String html = mvc.perform(get("/uygunluq").param("activity", "OTHER").param("otherText", "arıçılıq")
                        .param("budget", "0").param("budgetCustom", "150 000").param("size", "custom")
                        .param("minHa", "10").param("maxHa", "30").param("district", "").param("experience", "0"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Seçiminiz: Digər — arıçılıq · 150 000 AZN · 10–30 ha", "Sizə uyğun sahələr",
                "budgetCustom=150", "edit=1");
        mvc.perform(get("/uygunluq").param("activity", "OTHER").param("budget", "500000")
                        .param("size", "20-50").param("district", "").param("experience", "0"))
                .andExpect(content().string(containsString("Fəaliyyət növünü yazın.")))
                .andExpect(content().string(not(containsString("Sizə uyğun sahələr"))));
    }

    @Test
    void catalogSearchSortAndChips() throws Exception {
        String html = mvc.perform(get("/saheler").param("q", "fzl").param("open", "true").param("sort", "area-desc"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("FZL-001", "“fzl”", "Yalnız müsabiqədə olanlar", "Hamısını təmizlə").doesNotContain("AGD-001");
        assertThat(html.indexOf("FZL-001")).isLessThan(html.indexOf("FZL-002")); // 42.5 ha before 35 ha
        mvc.perform(get("/saheler").param("q", "yoxdur-bele-sahe"))
                .andExpect(content().string(containsString("Filtrləri sıfırla")));
        String home = mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(home).contains("Qarabağda sizə uyğun torpağı tapın", "rəsmi tərəfdaşlıq deyil", "Aktiv hərrac", "Necə işləyir")
                .doesNotContain("Müraciəti tezliklə bitən sahələr", "Regionlar üzrə", "Aktiv hərraclar");
    }
}

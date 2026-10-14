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
}

package az.aqroinvest;

import az.aqroinvest.repo.PlotRepository;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Bottom tab bar of the installed Android app (layout.html header; visible only via the app.css standalone media query). */
@SpringBootTest
@AutoConfigureMockMvc
class PwaTabBarTest {

    static final RequestPostProcessor INVESTOR = user("elvin@demo.az").roles("INVESTOR");
    static final RequestPostProcessor OPERATOR = user("operator@demo.az").roles("OPERATOR");
    static final RequestPostProcessor COMMISSION = user("komissiya@demo.az").roles("COMMISSION");
    static final RequestPostProcessor ADMIN = user("admin@demo.az").roles("ADMIN");

    @Autowired MockMvc mvc;
    @Autowired PlotRepository plots;

    @Test
    void anonymousGetsPublicTabsAndLogin() throws Exception {
        String nav = tabBar(get("/"));
        assertThat(hrefs(nav)).containsExactly("/", "/xerite", "/saheler", "/musabiqeler", "/giris");
        assertThat(nav).contains("aria-label=\"Əsas naviqasiya\"", "Ana səhifə", "Xəritə", "Sahələr", "Müsabiqələr", "Daxil ol");
        assertThat(activeHref(nav)).isEqualTo("/");
    }

    @Test
    void accountTabFollowsTheRole() throws Exception {
        String investor = tabBar(get("/kabinet").with(INVESTOR));
        assertThat(hrefs(investor)).containsExactly("/", "/xerite", "/saheler", "/musabiqeler", "/kabinet");
        assertThat(investor).contains("Kabinet").doesNotContain("Daxil ol");
        assertThat(activeHref(investor)).isEqualTo("/kabinet");

        for (RequestPostProcessor staff : new RequestPostProcessor[]{OPERATOR, COMMISSION, ADMIN}) {
            String nav = tabBar(get("/idare").with(staff));
            assertThat(hrefs(nav)).containsExactly("/", "/xerite", "/saheler", "/musabiqeler", "/idare");
            assertThat(activeHref(nav)).isEqualTo("/idare");
        }
        assertThat(activeHref(tabBar(get("/bildirisler").with(INVESTOR)))).isEqualTo("/kabinet");
        assertThat(activeHref(tabBar(get("/bildirisler").with(OPERATOR)))).isEqualTo("/idare");
    }

    @Test
    void activeTabFollowsThePage() throws Exception {
        long fzl = plots.findByCode("FZL-001").orElseThrow().getId();
        long fzl2 = plots.findByCode("FZL-002").orElseThrow().getId();
        assertThat(activeHref(tabBar(get("/xerite")))).isEqualTo("/xerite");
        assertThat(activeHref(tabBar(get("/saheler")))).isEqualTo("/saheler");
        assertThat(activeHref(tabBar(get("/saheler/" + fzl)))).isEqualTo("/saheler");
        assertThat(activeHref(tabBar(get("/muqayise").param("ids", fzl + "," + fzl2)))).isEqualTo("/saheler");
        assertThat(activeHref(tabBar(get("/musabiqeler")))).isEqualTo("/musabiqeler");
        assertThat(activeHref(tabBar(get("/giris")))).isEqualTo("/giris");
        assertThat(activeHref(tabBar(get("/qeydiyyat")))).isEqualTo("/giris");
        assertThat(activeHref(tabBar(get("/uygunluq")))).isNull(); // quiz and auctions stay in the ☰ menu
        assertThat(activeHref(tabBar(get("/herraclar")))).isNull();
    }

    @Test
    void labelsFollowTheLanguage() throws Exception {
        assertThat(tabBar(get("/").param("lang", "en")))
                .contains("aria-label=\"Main navigation\"", ">Home<", ">Map<", ">Plots<", ">Competitions<", ">Log in<");
        assertThat(tabBar(get("/").param("lang", "ru")))
                .contains("aria-label=\"Основная навигация\"", ">Главная<", ">Карта<", ">Участки<", ">Конкурсы<", ">Войти<");
        assertThat(tabBar(get("/kabinet").param("lang", "en").with(INVESTOR))).contains(">My account<");
        assertThat(tabBar(get("/idare").param("lang", "ru").with(ADMIN))).contains(">Кабинет<");
    }

    @Test
    void errorPagesKeepTheTabBar() throws Exception {
        for (MockHttpServletRequestBuilder r : new MockHttpServletRequestBuilder[]{error404(), error404().with(INVESTOR)}) {
            String nav = tabBar(r);
            assertThat(hrefs(nav)).hasSize(5).startsWith("/", "/xerite", "/saheler", "/musabiqeler");
            assertThat(activeHref(nav)).isNull();
        }
    }

    private static MockHttpServletRequestBuilder error404() {
        return get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404).accept(MediaType.TEXT_HTML);
    }

    /** The rendered <nav class="app-tabbar">…</nav> markup. */
    private String tabBar(RequestBuilder request) throws Exception {
        String html = mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        int start = html.indexOf("<nav class=\"app-tabbar\"");
        assertThat(start).as("tab bar rendered").isGreaterThanOrEqualTo(0);
        return html.substring(start, html.indexOf("</nav>", start) + "</nav>".length());
    }

    private static java.util.List<String> hrefs(String nav) {
        return Pattern.compile("<a [^>]*href=\"([^\"]*)\"").matcher(nav).results().map(m -> m.group(1)).toList();
    }

    /** href of the single tab marked aria-current="page", or null when no tab is active. */
    private static String activeHref(String nav) {
        Matcher m = Pattern.compile("<a [^>]*>").matcher(nav);
        String active = null;
        while (m.find()) {
            String tag = m.group();
            if (tag.contains("aria-current=\"page\"")) {
                assertThat(active).as("only one active tab").isNull();
                assertThat(tag).contains("class=\"on\"");
                active = hrefs(tag).get(0);
            } else {
                assertThat(tag).doesNotContain("class=\"on\"");
            }
        }
        return active;
    }
}

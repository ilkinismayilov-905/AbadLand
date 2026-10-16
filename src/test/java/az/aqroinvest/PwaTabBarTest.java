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
import java.util.List;
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

    static final List<String> TABS = List.of("/", "/xerite", "/ai", "/saheler");

    @Test
    void anonymousGetsPublicTabsAndMore() throws Exception {
        String nav = tabBar(get("/"));
        assertThat(hrefs(nav)).containsExactly("/", "/xerite", "/ai", "/saheler");
        assertThat(nav).contains("aria-label=\"Əsas naviqasiya\"", "Ana səhifə", "Xəritə", ">AI<", "Sahələr", "Daha çox",
                "popovertarget=\"app-more\"");
        assertThat(activeHref(nav)).isEqualTo("/");
        assertThat(moreOn(nav)).isFalse();
        String more = moreSheet(get("/"));
        assertThat(hrefs(more)).containsExactly("/musabiqeler", "/herraclar", "/uygunluq", "/giris", "/qeydiyyat");
        assertThat(more).doesNotContain("/cixis", "/kabinet", "/idare", "/bildirisler");
    }

    @Test
    void moreSheetFollowsTheRole() throws Exception {
        String investor = moreSheet(get("/").with(INVESTOR));
        assertThat(hrefs(investor)).containsExactly("/musabiqeler", "/herraclar", "/uygunluq", "/kabinet", "/bildirisler");
        assertThat(investor).contains("action=\"/cixis\"", "method=\"post\"", "name=\"_csrf\"", "Çıxış", "(İnvestor)")
                .doesNotContain("/giris", "/qeydiyyat", "/idare");
        for (RequestPostProcessor staff : new RequestPostProcessor[]{OPERATOR, COMMISSION, ADMIN}) {
            String more = moreSheet(get("/").with(staff));
            assertThat(hrefs(more)).containsExactly("/musabiqeler", "/herraclar", "/uygunluq", "/idare", "/bildirisler");
            assertThat(more).contains("action=\"/cixis\"", "name=\"_csrf\"").doesNotContain("/kabinet", "/giris");
            assertThat(hrefs(tabBar(get("/idare").with(staff)))).isEqualTo(TABS);
        }
    }

    /** Every destination of the ☰ menu (#site-menu) is still reachable from the tabs or the More sheet, for every role. */
    @Test
    void nothingFromTheHamburgerIsLost() throws Exception {
        for (RequestBuilder r : new RequestBuilder[]{get("/"), get("/").with(INVESTOR), get("/").with(OPERATOR), get("/").with(COMMISSION), get("/").with(ADMIN)}) {
            String html = page(r);
            String menu = html.substring(html.indexOf("<div id=\"site-menu\""), html.indexOf("<nav class=\"lang-switch\""));
            List<String> reachable = new java.util.ArrayList<>(hrefs(tabBar(r)));
            String more = moreSheet(r);
            reachable.addAll(hrefs(more));
            assertThat(reachable).containsAll(hrefs(menu));
            if (menu.contains("action=\"/cixis\"")) assertThat(more).contains("action=\"/cixis\"");
            // The bell (notifications) stays in the header for logged-in users; More lists it too
            if (html.contains("href=\"/bildirisler\" class=\"relative")) assertThat(hrefs(more)).contains("/bildirisler");
        }
    }

    @Test
    void activeTabFollowsThePage() throws Exception {
        long fzl = plots.findByCode("FZL-001").orElseThrow().getId();
        long fzl2 = plots.findByCode("FZL-002").orElseThrow().getId();
        assertThat(activeHref(tabBar(get("/xerite")))).isEqualTo("/xerite");
        assertThat(activeHref(tabBar(get("/ai")))).isEqualTo("/ai");
        assertThat(activeHref(tabBar(get("/saheler")))).isEqualTo("/saheler");
        assertThat(activeHref(tabBar(get("/saheler/" + fzl)))).isEqualTo("/saheler");
        assertThat(activeHref(tabBar(get("/muqayise").param("ids", fzl + "," + fzl2)))).isEqualTo("/saheler");
        for (String path : List.of("/musabiqeler", "/herraclar", "/uygunluq", "/giris", "/qeydiyyat")) {
            String nav = tabBar(get(path));
            assertThat(activeHref(nav)).as(path).isNull();
            assertThat(moreOn(nav)).as(path).isTrue();
        }
        assertThat(moreSheet(get("/herraclar"))).contains("href=\"/herraclar\" aria-current=\"page\"");
        assertThat(moreOn(tabBar(get("/kabinet").with(INVESTOR)))).isTrue();
        assertThat(moreOn(tabBar(get("/bildirisler").with(INVESTOR)))).isTrue();
        assertThat(moreOn(tabBar(get("/idare").with(OPERATOR)))).isTrue();
        assertThat(moreOn(tabBar(get("/")))).isFalse();
    }

    @Test
    void labelsFollowTheLanguage() throws Exception {
        assertThat(tabBar(get("/").param("lang", "en")))
                .contains("aria-label=\"Main navigation\"", ">Home<", ">Map<", ">AI<", ">Plots<", ">More<");
        assertThat(tabBar(get("/").param("lang", "ru")))
                .contains("aria-label=\"Основная навигация\"", ">Главная<", ">Карта<", ">ИИ<", ">Участки<", ">Ещё<");
        assertThat(moreSheet(get("/").param("lang", "en").with(INVESTOR))).contains(">My account<", ">Log out<", ">Competitions<");
        assertThat(moreSheet(get("/").param("lang", "ru").with(ADMIN))).contains(">Управление<", ">Выйти<", ">Аукционы<");
    }

    @Test
    void hamburgerKeepsItsHookAndTheSheetIsAPopover() throws Exception {
        String html = page(get("/"));
        assertThat(html).contains("class=\"site-menu-btn ", "aria-controls=\"site-menu\"", "<div id=\"app-more\" class=\"app-more\" popover",
                "popovertargetaction=\"hide\"");
    }

    @Test
    void errorPagesKeepTheTabBar() throws Exception {
        for (MockHttpServletRequestBuilder r : new MockHttpServletRequestBuilder[]{error404(), error404().with(INVESTOR)}) {
            String nav = tabBar(r);
            assertThat(hrefs(nav)).isEqualTo(TABS);
            assertThat(nav).contains("popovertarget=\"app-more\"");
            assertThat(activeHref(nav)).isNull();
        }
    }

    private static MockHttpServletRequestBuilder error404() {
        return get("/error").requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404).accept(MediaType.TEXT_HTML);
    }

    private String page(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** The rendered More sheet (<div id="app-more" popover>…</div>). */
    private String moreSheet(RequestBuilder request) throws Exception {
        String html = page(request);
        int start = html.indexOf("<div id=\"app-more\"");
        assertThat(start).as("More sheet rendered").isGreaterThanOrEqualTo(0);
        int close = html.indexOf("app-more-close", start);
        return html.substring(start, html.indexOf("</div>", close) + "</div>".length());
    }

    private static boolean moreOn(String nav) {
        Matcher m = Pattern.compile("<button [^>]*class=\"tab-more([^\"]*)\"").matcher(nav);
        assertThat(m.find()).as("More tab rendered").isTrue();
        return m.group(1).contains("on");
    }

    /** The rendered <nav class="app-tabbar">…</nav> markup. */
    private String tabBar(RequestBuilder request) throws Exception {
        String html = page(request);
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

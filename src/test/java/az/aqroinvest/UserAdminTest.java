package az.aqroinvest;

import az.aqroinvest.domain.Role;
import az.aqroinvest.domain.User;
import az.aqroinvest.repo.AuditLogRepository;
import az.aqroinvest.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:useradmin;DB_CLOSE_DELAY=-1;NON_KEYWORDS=USER,VALUE,YEAR,PERIOD,AT,LOCAL")
class UserAdminTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuditLogRepository audit;

    static final RequestPostProcessor ADMIN = user("admin@demo.az").roles("ADMIN");

    long id(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    boolean audited(String action, String email) {
        return audit.findAll().stream().anyMatch(a -> action.equals(a.getAction()) && email.equals(a.getEntityId()));
    }

    @Test
    void onlyAdminSeesThePage() throws Exception {
        mvc.perform(get("/idare/istifadeciler").with(user("operator@demo.az").roles("OPERATOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/idare/istifadeciler").with(user("komissiya@demo.az").roles("COMMISSION"))).andExpect(status().isForbidden());
        mvc.perform(get("/idare/istifadeciler").with(user("elvin@demo.az").roles("INVESTOR"))).andExpect(status().isForbidden());
        mvc.perform(post("/idare/istifadeciler/1/aktiv").param("active", "false").with(user("operator@demo.az").roles("OPERATOR")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/idare/istifadeciler").with(ADMIN)).andExpect(status().isOk()).andExpect(content().string(allOf(
                containsString("elvin@demo.az"), containsString("Aktiv"), containsString("Yeni işçi"), containsString("İstifadəçilər"))));
        mvc.perform(get("/idare").with(user("operator@demo.az").roles("OPERATOR"))).andExpect(content().string(not(containsString("/idare/istifadeciler"))));
    }

    @Test
    void createStaffShowsPasswordOnceAndItWorks() throws Exception {
        var res = mvc.perform(post("/idare/istifadeciler").with(ADMIN).with(csrf()).param("fullName", "Yeni Operator")
                        .param("email", "New.Op@demo.az").param("phone", "+994501112233").param("role", "OPERATOR"))
                .andExpect(redirectedUrl("/idare/istifadeciler")).andReturn();
        String pw = (String) res.getFlashMap().get("tempPassword");
        assertThat(pw).hasSize(12).matches("[A-Za-z2-9]+");
        User created = users.findByEmailIgnoreCase("new.op@demo.az").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.OPERATOR);
        assertThat(created.getPasswordHash()).doesNotContain(pw).startsWith("$2");
        assertThat(audited("USER_CREATED", "new.op@demo.az")).isTrue();
        assertThat(audit.findAll()).noneMatch(a -> a.getDetails() != null && a.getDetails().contains(pw));
        // shown once: the next page view has no password
        mvc.perform(get("/idare/istifadeciler").with(ADMIN)).andExpect(content().string(not(containsString(pw))));
        // the temporary password logs in, and last login is recorded
        mvc.perform(formLogin("/giris").user("new.op@demo.az").password(pw)).andExpect(redirectedUrl("/idare"));
        assertThat(users.findByEmailIgnoreCase("new.op@demo.az").orElseThrow().getLastLoginAt()).isNotNull();

        // INVESTOR (or garbage) role rejected; duplicate e-mail rejected; validation errors in Azerbaijani
        mvc.perform(post("/idare/istifadeciler").with(ADMIN).with(csrf()).param("fullName", "X").param("email", "x@demo.az")
                .param("phone", "+994501112233").param("role", "INVESTOR")).andExpect(content().string(containsString("Yalnız Operator")));
        assertThat(users.findByEmailIgnoreCase("x@demo.az")).isEmpty();
        mvc.perform(post("/idare/istifadeciler").with(ADMIN).with(csrf()).param("fullName", "X").param("email", "elvin@demo.az")
                .param("phone", "+994501112233").param("role", "ADMIN")).andExpect(content().string(containsString("artıq istifadə olunur")));
        mvc.perform(post("/idare/istifadeciler").with(ADMIN).with(csrf()).param("fullName", "").param("email", "bad")
                .param("phone", "1").param("role", "")).andExpect(content().string(allOf(containsString("Ad və soyad tələb olunur"),
                containsString("E-poçt düzgün deyil"), containsString("Rolu seçin"))));
    }

    @Test
    void deactivateBlocksLoginAndEndsOpenSession() throws Exception {
        // operator is logged in with a real session
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin("/giris").user("operator@demo.az").password("Demo1234"))
                .andExpect(redirectedUrl("/idare")).andReturn().getRequest().getSession();
        mvc.perform(get("/idare").session(session)).andExpect(status().isOk());

        // admin cannot deactivate self
        mvc.perform(post("/idare/istifadeciler/" + id("admin@demo.az") + "/aktiv").param("active", "false").with(ADMIN).with(csrf()))
                .andExpect(flash().attribute("error", containsString("Özünüzü")));
        assertThat(users.findByEmailIgnoreCase("admin@demo.az").orElseThrow().isActive()).isTrue();

        mvc.perform(post("/idare/istifadeciler/" + id("operator@demo.az") + "/aktiv").param("active", "false").with(ADMIN).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(audited("USER_DISABLED", "operator@demo.az")).isTrue();
        mvc.perform(get("/idare").session(session)).andExpect(redirectedUrl("/giris?deaktiv"));          // open session ended
        mvc.perform(formLogin("/giris").user("operator@demo.az").password("Demo1234")).andExpect(redirectedUrl("/giris?deaktiv"));
        mvc.perform(get("/giris").param("deaktiv", "")).andExpect(content().string(containsString("Hesabınız deaktiv edilib")));

        mvc.perform(post("/idare/istifadeciler/" + id("operator@demo.az") + "/aktiv").param("active", "true").with(ADMIN).with(csrf()))
                .andExpect(flash().attributeExists("success"));
        assertThat(audited("USER_ENABLED", "operator@demo.az")).isTrue();
        mvc.perform(formLogin("/giris").user("operator@demo.az").password("Demo1234")).andExpect(redirectedUrl("/idare"));
    }

    @Test
    void registrationIgnoresPostedRole() throws Exception {
        mvc.perform(post("/qeydiyyat").with(csrf()).param("fullName", "Haker").param("email", "haker@demo.az")
                        .param("phone", "+994501112233").param("password", "Parol1234").param("investorType", "INDIVIDUAL")
                        .param("fin", "1ABC234").param("consent", "true")
                        .param("role", "ADMIN").param("active", "false").param("id", "1"))
                .andExpect(redirectedUrl("/giris"));
        User u = users.findByEmailIgnoreCase("haker@demo.az").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.INVESTOR);
        assertThat(u.isActive()).isTrue();
        assertThat(u.getId()).isNotEqualTo(1L);
        assertThat(users.findById(1L).orElseThrow().getEmail()).isEqualTo("elvin@demo.az");
    }
}

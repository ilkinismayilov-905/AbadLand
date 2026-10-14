package az.aqroinvest;

import az.aqroinvest.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Step1SmokeTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PlotRepository plots;
    @Autowired ApplicationRepository applications;
    @Autowired FarmProjectRepository farms;
    @Autowired CalendarTaskRepository tasks;
    @Autowired AuditLogRepository audit;

    @Test
    void seedIsLoaded() {
        assertThat(plots.count()).isGreaterThanOrEqualTo(15);
        assertThat(applications.count()).isEqualTo(8);
        assertThat(farms.count()).isEqualTo(3); // 2 from applications + 1 auction winner (HRC-2026-00)
        assertThat(tasks.count()).isGreaterThan(0);
    }

    @Test
    void demoUsersLandOnTheirPages() throws Exception {
        mvc.perform(formLogin("/giris").user("elvin@demo.az").password("Demo1234")).andExpect(redirectedUrl("/kabinet"));
        mvc.perform(formLogin("/giris").user("rashad@demo.az").password("Demo1234")).andExpect(redirectedUrl("/kabinet"));
        for (String staff : new String[]{"operator", "komissiya", "admin"})
            mvc.perform(formLogin("/giris").user(staff + "@demo.az").password("Demo1234")).andExpect(redirectedUrl("/idare"));
        mvc.perform(formLogin("/giris").user("elvin@demo.az").password("wrong")).andExpect(redirectedUrl("/giris?xeta"));
        assertThat(audit.findAll()).anyMatch(a -> "LOGIN".equals(a.getAction()));
    }

    @Test
    void investorIsForbiddenOnStaffArea() throws Exception {
        mvc.perform(get("/idare").with(user("elvin@demo.az").roles("INVESTOR"))).andExpect(status().isForbidden());
        mvc.perform(get("/idare").with(user("operator@demo.az").roles("OPERATOR"))).andExpect(status().isOk());
        mvc.perform(get("/kabinet").with(user("elvin@demo.az").roles("INVESTOR"))).andExpect(status().isOk());
    }

    @Test
    void publicPagesRender() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/giris")).andExpect(status().isOk());
        mvc.perform(get("/qeydiyyat")).andExpect(status().isOk());
    }

    @Test
    void registrationValidatesAndCreatesInvestor() throws Exception {
        mvc.perform(post("/qeydiyyat").with(csrf()).param("fullName", "Test").param("email", "bad")
                        .param("phone", "+994501112233").param("password", "short").param("investorType", "INDIVIDUAL"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form", "email", "password", "fin", "consent"));
        mvc.perform(post("/qeydiyyat").with(csrf()).param("fullName", "Yeni İnvestor").param("email", "new@demo.az")
                        .param("phone", "+994501112233").param("password", "Parol1234").param("investorType", "LEGAL")
                        .param("voen", "1234567890").param("companyName", "Test MMC").param("consent", "true"))
                .andExpect(redirectedUrl("/giris"));
        assertThat(users.findByEmailIgnoreCase("new@demo.az")).isPresent();
        mvc.perform(formLogin("/giris").user("new@demo.az").password("Parol1234")).andExpect(redirectedUrl("/kabinet"));
    }
}

package az.aqroinvest.config;

import az.aqroinvest.repo.UserRepository;
import az.aqroinvest.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmailIgnoreCase(email)
                .map(u -> org.springframework.security.core.userdetails.User.withUsername(u.getEmail())
                        .password(u.getPasswordHash())
                        .roles(u.getRole().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(email));
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuditService audit,
                                    @Value("${app.external-api-key}") String externalApiKey) throws Exception {
        AuthenticationSuccessHandler onLogin = (req, res, auth) -> {
            String role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
            audit.log(auth.getName(), role, "LOGIN", "User", auth.getName(), null);
            // back to the page that required login (e.g. "Müraciət et"), else the role's home
            var saved = new org.springframework.security.web.savedrequest.HttpSessionRequestCache().getRequest(req, res);
            String path = saved == null ? "" : java.net.URI.create(saved.getRedirectUrl()).getPath();
            // only our own pages (browsers probe /.well-known/... which must not become the landing page)
            boolean savedOk = saved != null && saved.getMethod().equals("GET")
                    && ("INVESTOR".equals(role) ? path.startsWith("/kabinet/") || path.startsWith("/pay-layiheleri")
                    : path.startsWith("/idare/")) || saved != null && path.equals("/bildirisler");
            res.sendRedirect(savedOk ? saved.getRedirectUrl() : req.getContextPath() + ("INVESTOR".equals(role) ? "/kabinet" : "/idare"));
        };
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/xerite", "/saheler/**", "/muqayise", "/musabiqeler", "/uygunluq/**",
                                "/qeydiyyat", "/giris", "/error", "/css/**", "/js/**", "/favicon.ico",
                                "/api/v1/plots/**", "/api/v1/competitions", "/api/v1/external/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/pay-layiheleri", "/pay-layiheleri/*").permitAll()
                        .requestMatchers("/kabinet/**").hasRole("INVESTOR")
                        .requestMatchers("/idare/audit").hasAnyRole("COMMISSION", "ADMIN")
                        .requestMatchers("/idare/**").hasAnyRole("OPERATOR", "COMMISSION", "ADMIN")
                        .anyRequest().authenticated())
                .formLogin(f -> f.loginPage("/giris").successHandler(onLogin).failureUrl("/giris?xeta").permitAll())
                .logout(l -> l.logoutUrl("/cixis").logoutSuccessUrl("/").permitAll())
                // REST: 401 instead of a redirect to the login page
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                        new org.springframework.security.web.util.matcher.AntPathRequestMatcher("/api/**")))
                .addFilterBefore(new ExternalApiKeyFilter(externalApiKey), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Guards /api/v1/external/** with the X-API-Key header. */
    static class ExternalApiKeyFilter extends OncePerRequestFilter {
        private final byte[] key;

        ExternalApiKeyFilter(String key) {
            this.key = key.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest req) {
            return !req.getRequestURI().startsWith(req.getContextPath() + "/api/v1/external/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws IOException, jakarta.servlet.ServletException {
            String given = req.getHeader("X-API-Key");
            if (given == null || !MessageDigest.isEqual(key, given.getBytes(StandardCharsets.UTF_8))) {
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
                return;
            }
            chain.doFilter(req, res);
        }
    }
}

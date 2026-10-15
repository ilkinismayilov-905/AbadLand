package az.aqroinvest.config;

import az.aqroinvest.web.I18n;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;

/** Language: ?lang=az|en|ru switches and is remembered in the "lang" cookie; default AZ. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public MessageSource messageSource() {
        return I18n.bundles();
    }

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver r = new CookieLocaleResolver("lang");
        r.setDefaultLocale(I18n.AZ);
        r.setCookieMaxAge(Duration.ofDays(365));
        return r;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor lang = new LocaleChangeInterceptor() {
            @Override
            public boolean preHandle(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res, Object h)
                    throws jakarta.servlet.ServletException {
                String v = req.getParameter("lang");
                return v == null || I18n.LANGS.contains(v) ? super.preHandle(req, res, h) : true; // ignore unsupported values
            }
        };
        lang.setParamName("lang");
        registry.addInterceptor(lang);
    }
}

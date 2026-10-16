package az.aqroinvest;

import az.aqroinvest.web.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class I18nTest {

    final I18n i18n = new I18n(I18n.bundles());

    @AfterEach
    void reset() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void storedNotificationRendersInReadersLanguage() {
        String stored = I18n.stored("apply.notify.taskDelayed.PLANTING", I18n.keyArg("crop.HAZELNUT"), "FZL-001");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(i18n.render(stored)).contains("Hazelnut", "FZL-001");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
        assertThat(i18n.render(stored)).contains("Фундук", "FZL-001");
        LocaleContextHolder.setLocale(I18n.AZ);
        assertThat(i18n.render(stored)).contains("Fındıq");
        assertThat(i18n.render("Köhnə mətn")).isEqualTo("Köhnə mətn"); // legacy seed text passes through
    }

    @Test
    void unsupportedLanguageFallsBackToAzerbaijani() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThat(I18n.t("enum.ActivityType.HORTICULTURE")).isEqualTo("Bağçılıq");
        assertThat(i18n.lang()).isEqualTo("az");
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(I18n.t("enum.ActivityType.HORTICULTURE")).isEqualTo("Horticulture");
        assertThat(i18n.js()).containsEntry("js.auction.startPrice", "Starting price"); // JS dictionary follows the language
    }

    @Test
    void everyBundleHasTheSameKeysInAllLanguages() throws IOException {
        for (String base : I18n.BASENAMES) {
            Set<String> az = keys(base + ".properties");
            assertThat(az).as(base).isNotEmpty();
            assertThat(keys(base + "_en.properties")).as(base + " en").isEqualTo(az);
            assertThat(keys(base + "_ru.properties")).as(base + " ru").isEqualTo(az);
        }
    }

    private static Set<String> keys(String file) throws IOException {
        try (InputStream in = I18nTest.class.getClassLoader().getResourceAsStream(file)) {
            assertThat(in).as(file).isNotNull();
            Properties p = new Properties();
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return new TreeSet<>(p.stringPropertyNames());
        }
    }
}

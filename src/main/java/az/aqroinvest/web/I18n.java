package az.aqroinvest.web;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * UI texts in az (default) / en / ru. Templates use #{key}; Java uses I18n.t("key", args) — resolved in the
 * current request's language (cookie "lang"), AZ outside requests (scheduler, tests).
 * Bundles: src/main/resources/messages*.properties + i18n/<group>*.properties (one group per feature area).
 */
@Component("i18n")
public class I18n {

    public static final Locale AZ = Locale.forLanguageTag("az");
    public static final List<String> LANGS = List.of("az", "en", "ru");
    /** Every bundle; add a new group here. */
    public static final String[] BASENAMES = {"messages", "i18n/enums", "i18n/common", "i18n/plots", "i18n/apply", "i18n/staff", "i18n/auction"};
    /** Persisted texts (notifications) are stored as key + args and rendered in the reader's language. */
    private static final String STORED = "@i18n:", SEP = "\u001F", KEY_ARG = "@key:";

    private static MessageSource source = bundles();

    static {
        LocaleContextHolder.setDefaultLocale(AZ);
    }

    public I18n(MessageSource messageSource) {
        source = messageSource;
    }

    public static ResourceBundleMessageSource bundles() {
        ResourceBundleMessageSource ms = new ResourceBundleMessageSource();
        ms.setBasenames(BASENAMES);
        ms.setDefaultEncoding("UTF-8");
        ms.setFallbackToSystemLocale(false); // unknown language → messages.properties (AZ), never the server's locale
        return ms;
    }

    /** Text for key in the current language; the key itself if missing (visible, never a crash). */
    public static String t(String key, Object... args) {
        return source.getMessage(key, args, key, locale());
    }

    public static Locale locale() {
        Locale l = LocaleContextHolder.getLocale();
        return LANGS.contains(l.getLanguage()) ? l : AZ;
    }

    /** Value to persist (e.g. Notification.message) so each reader sees it in their own language. */
    public static String stored(String key, Object... args) {
        StringBuilder b = new StringBuilder(STORED).append(key);
        for (Object a : args) b.append(SEP).append(a);
        return b.toString();
    }

    /** Argument for stored(...) that is itself a message key, resolved when the reader renders it. */
    public static String keyArg(String key) {
        return KEY_ARG + key;
    }

    /** Renders a stored value; plain legacy text (seed data) is returned as is. Template: ${@i18n.render(x)} */
    public String render(String value) {
        if (value == null || !value.startsWith(STORED)) return value;
        String[] parts = value.substring(STORED.length()).split(SEP, -1);
        Object[] args = Arrays.copyOfRange(parts, 1, parts.length);
        for (int i = 0; i < args.length; i++) // an arg written as keyArg("crop.HAZELNUT") is translated too
            if (((String) args[i]).startsWith(KEY_ARG)) args[i] = t(((String) args[i]).substring(KEY_ARG.length()));
        return t(parts[0], args);
    }

    /** Keys starting with "js." for the current language → window.I18N for static JS files. */
    public Map<String, String> js() {
        Map<String, String> m = new TreeMap<>();
        for (String base : BASENAMES) {
            ResourceBundle b;
            try {
                b = ResourceBundle.getBundle(base, locale(), ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES));
            } catch (MissingResourceException e) {
                continue;
            }
            for (String k : Collections.list(b.getKeys())) if (k.startsWith("js.")) m.put(k, b.getString(k));
        }
        return m;
    }

    public String lang() {
        return locale().getLanguage();
    }
}

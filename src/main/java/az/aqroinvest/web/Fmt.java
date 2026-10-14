package az.aqroinvest.web;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Formatting for templates: ${@fmt.money(x)} → "410 000 AZN". */
@Component("fmt")
public class Fmt {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public String money(Number n) {
        return n == null ? "—" : num(n) + " AZN";
    }

    /** Integer with space grouping: 410 000 */
    public String num(Number n) {
        return n == null ? "—" : String.format("%,d", Math.round(n.doubleValue())).replace(',', ' ');
    }

    /** One decimal, comma separator: 42,5 */
    public String dec(Number n) {
        if (n == null) return "—";
        double d = n.doubleValue();
        return d == Math.rint(d) ? num(d) : String.format(java.util.Locale.ROOT, "%.1f", d).replace('.', ',');
    }

    /** Days from today (Asia/Baku) until d; negative if past. */
    public long daysUntil(LocalDate d) {
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(az.aqroinvest.service.AuditService.BAKU), d);
    }

    public String date(LocalDate d) {
        return d == null ? "—" : d.format(DATE);
    }

    public String dateTime(LocalDateTime d) {
        return d == null ? "—" : d.format(DATE_TIME);
    }
}

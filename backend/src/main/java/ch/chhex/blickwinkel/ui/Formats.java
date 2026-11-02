package ch.chhex.blickwinkel.ui;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Formatierung für die Templates, aufrufbar als {@code ${@fmt.time(...)}}.
 * Die Datenbank rechnet in UTC, angezeigt wird Schweizer Zeit.
 */
@Component("fmt")
public class Formats {

    static final ZoneId ZONE = ZoneId.of("Europe/Zurich");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm", Locale.GERMAN);

    /** z.B. «02.11. 18:25», leer bei null. */
    public String time(OffsetDateTime t) {
        return t == null ? "" : TIME.format(t.atZoneSameInstant(ZONE));
    }

    /** Zeitspanne einer Story, z.B. «01.11. 16:33 – 23:35» oder bei mehreren Tagen mit Datum. */
    public String span(OffsetDateTime from, OffsetDateTime to) {
        if (from == null || to == null) {
            return time(from != null ? from : to);
        }
        var a = from.atZoneSameInstant(ZONE);
        var b = to.atZoneSameInstant(ZONE);
        if (Duration.between(a, b).toMinutes() < 1) {
            return time(from);
        }
        String end = a.toLocalDate().equals(b.toLocalDate())
                ? DateTimeFormatter.ofPattern("HH:mm").format(b)
                : TIME.format(b);
        return time(from) + " – " + end;
    }

    /** Ähnlichkeit mit zwei Nachkommastellen, leer bei null. */
    public String sim(Number value) {
        return value == null ? "" : String.format(Locale.ROOT, "%.2f", value.doubleValue());
    }
}

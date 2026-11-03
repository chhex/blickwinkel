package ch.chhex.blickwinkel.translation;

import java.util.List;
import java.util.Optional;

/**
 * Übersetzungsanbieter. Heute DeepL; ein lokales LLM wäre eine weitere Implementierung.
 */
public interface TranslationClient {

    /** Kurzname für die Datenbank, z.B. "deepl". */
    String provider();

    /** Ist der Anbieter konfiguriert (z.B. API-Schlüssel vorhanden)? */
    boolean isConfigured();

    /**
     * Übersetzt mehrere Texte in einem Aufruf, Ergebnis in derselben Reihenfolge.
     *
     * @param sourceLang Ausgangssprache als ISO-Code (de, fr, ...), darf null sein
     * @param targetLang Zielsprache als ISO-Code (de, en, es)
     * @throws TranslationException mit einer Meldung, die man dem Benutzer zeigen kann
     */
    Result translate(List<String> texts, String sourceLang, String targetLang);

    /** Verbrauchtes und verfügbares Kontingent, falls der Anbieter das kennt. */
    Optional<Usage> usage();

    record Result(List<String> texts, String detectedSourceLang) {
    }

    record Usage(long characterCount, long characterLimit) {
    }
}

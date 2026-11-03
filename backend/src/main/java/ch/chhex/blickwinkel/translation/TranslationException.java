package ch.chhex.blickwinkel.translation;

/** Fehler beim Übersetzen, mit einer Meldung, die direkt im UI angezeigt werden kann. */
public class TranslationException extends RuntimeException {

    public TranslationException(String message) {
        super(message);
    }

    public TranslationException(String message, Throwable cause) {
        super(message, cause);
    }
}

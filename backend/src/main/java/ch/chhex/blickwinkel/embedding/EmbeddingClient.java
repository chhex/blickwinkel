package ch.chhex.blickwinkel.embedding;

import java.util.List;

/** Erzeugt Embeddings für Texte. Ergebnis in derselben Reihenfolge wie die Eingabe. */
public interface EmbeddingClient {

    List<float[]> embed(List<String> texts);
}

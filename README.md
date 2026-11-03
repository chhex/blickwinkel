# Blickwinkel

Privater News-Vergleich à la Ground News: **Ingest → Embedding → Clustering**, dazu ein
serverseitiges UI.

```
sources.yml ─► SourceSync ─► source, source_feed
RSS-Feeds ──► IngestService ─► article (NEW)
                 EmbeddingService ─► article (EMBEDDED)   Ollama bge-m3
                 ClusteringService ─► article (CLUSTERED) + story
                                     UI  /stories, /einzelmeldungen, /status
                                     API /api/stories
```

Die Pipeline ist über die Status-Spalte in `article` entkoppelt. Ein weiterer
Service (z.B. Scoring in Quarkus) kann auf `CLUSTERED` aufsetzen, ohne diesen
Code zu kennen.

## Stack

- Spring Boot 3.5, Java 21
- Spring `JdbcClient` mit handgeschriebenem SQL, Flyway
- Postgres 17 + pgvector
- Ollama mit `bge-m3` (multilingual, 1024 Dimensionen, DE/FR/ES/EN im selben Raum)
- Rome (RSS/Atom), jsoup (HTML aus Teasern entfernen)
- UI: Thymeleaf, htmx 2.0.11, Pico.css 2.1.1 (beide versioniert unter `static/vendor/`, kein CDN)

## Secrets

Passwörter stehen nur in `.env` (nicht im Repo). Vorlage: `.env.example`.
Ohne `.env` brechen Compose und App bewusst ab, es gibt kein Standardpasswort.

## Lokal entwickeln (Mac)

```bash
cp .env.example .env          # einmalig, Passwort setzen
docker compose up -d          # Postgres, Ollama, lädt bge-m3 einmalig (~1.2 GB)
docker compose logs -f ollama-pull   # warten bis "success"
cd backend 
mvn spring-boot:run           # liest .env selbst, Flyway migriert beim Start
```

Der Build braucht keine Datenbank, nur der laufende Dienst. Lokal startet
Compose die App **nicht** mit (Profil `app`), sie läuft über Maven.

**Bestehende Datenbank:** Postgres übernimmt das Passwort nur beim allerersten
Start eines leeren Volumes. Wer schon Daten hat, setzt in `.env` das bisherige
Passwort (`blickwinkel`) oder beginnt neu mit `docker compose down -v`.

Nach ein paar Minuten: **http://localhost:8080** im Browser, oder per API:

```bash
curl 'http://localhost:8080/api/stories?hours=24&minSources=2' | jq
```

## UI

| Seite | Inhalt |
|---|---|
| `/stories` | Stories mit Quellen-Badges; Suche, Zeitraum oder Datumsbereich, Mindestanzahl Quellen, Sortierung |
| `/stories/{id}` | alle Schlagzeilen einer Story nebeneinander, Meinungsbeiträge markiert |
| `/einzelmeldungen` | Artikel ohne zweite Quelle, mit der ähnlichsten fremden Story. Markiert sind Fälle knapp unter dem Schwellwert: das Werkzeug zum Kalibrieren |
| `/status` | Pipeline-Zähler und Zustand jedes Feeds |

**Story-Titel:** der Titel des Artikels, der dem Centroid am nächsten liegt.

**Suche:** Teilwort-Suche über Titel und Teaser, ohne Gross/Klein und Akzente
(«Zurich» findet «Zürich», «Armee» findet «Armeebudget»). Mehrere Begriffe müssen alle
im selben Artikel vorkommen. Eine Story erscheint, wenn mindestens einer ihrer Artikel
passt. Technisch: Trigramm-Index (`pg_trgm`) auf einem normalisierten Ausdruck
(`V4__search.sql`), bewusst ohne sprachabhängiges Stemming, weil vier Sprachen gemischt
sind und deutsche Komposita sonst nicht gefunden würden.

**Zeitraum:** entweder «die letzten N Stunden» oder ein Datumsbereich (Schweizer Zeit,
«bis» einschliesslich). Eine Story zählt, wenn sich ihr Zeitraum mit dem gewählten
überschneidet. Alle Filter stehen in der URL und lassen sich als Lesezeichen speichern.

**Übersetzung:** Im Story-Detail hat jeder Artikel Knöpfe **DE · EN · ES** (die eigene
Sprache fehlt). Ein Klick übersetzt Titel und Teaser dieses einen Artikels über DeepL;
«Original» schaltet zurück. Nichts wird automatisch übersetzt. Jede Übersetzung wird in
`article_translation` gespeichert und danach nicht mehr beim Anbieter angefragt.
Den Verbrauch des DeepL-Kontingents zeigt die Statusseite.

- Einschalten: `DEEPL_API_KEY` in `.env` setzen (auf dem NUC: `/opt/blickwinkel/.env`),
  danach neu deployen bzw. neu starten. Ohne Schlüssel erscheinen keine Knöpfe.
- Gratis-Schlüssel enden auf `:fx` und laufen automatisch über `api-free.deepl.com`.
- Der Anbieter steckt hinter `TranslationClient`; ein lokales LLM wäre eine weitere
  Implementierung.
- Bewertungen (z.B. Clickbait) laufen immer auf dem Original, nie auf der Übersetzung.

**htmx:** Filter laden nur die Liste neu. Der Controller erkennt htmx am Header
`HX-Request` und rendert dann nur das Thymeleaf-Fragment statt der ganzen Seite.
Die Abfragen liegen in `StoryQueryService` und werden auch von der REST-API benutzt.

Kein Login: Das UI ist für das Heimnetz gedacht. Vor einer Freigabe nach aussen
braucht es einen Zugangsschutz.

**Mac-Hinweis:** Ollama in Docker läuft auf dem Mac ohne GPU. Für Embeddings
reicht das, schneller ist die native Ollama-App. Dann den `ollama`-Service in
`compose.yaml` weglassen und `ollama pull bge-m3` lokal ausführen; die
`base-url` bleibt `http://localhost:11434`.

## Deployment auf den NUC

Push auf `main` → Self-hosted Runner auf dem NUC → `docker compose --profile app up -d --build`.
Workflow: `.github/workflows/deploy.yml`. Auf dem NUC braucht es nur Docker,
gebaut wird im Container (`Dockerfile`, mehrstufig).

**Einmalig einrichten:**

1. Env-Datei fest auf dem NUC ablegen (überlebt jedes Deployment):
   ```bash
   sudo mkdir -p /opt/blickwinkel
   sudo cp .env.example /opt/blickwinkel/.env     # Passwort setzen!
   sudo chown <runner-user> /opt/blickwinkel/.env
   sudo chmod 600 /opt/blickwinkel/.env
   ```
2. Runner registrieren: GitHub → Repo → Settings → Actions → Runners →
   *New self-hosted runner* (Linux, x64). Die dort angezeigten Befehle ausführen,
   bei `config.sh` das Label **`nuc`** ergänzen.
3. Runner als Dienst starten (`sudo ./svc.sh install && sudo ./svc.sh start`).
4. Der Runner-User muss Docker benutzen dürfen: `sudo usermod -aG docker <runner-user>`.
   Hinweis: Die Docker-Gruppe ist faktisch root-gleich.

**Sicherheit:** Der Workflow löst nur bei `push` auf `main` und manuell aus.
Solange ein Self-hosted Runner dranhängt, nie `pull_request` ergänzen.
Das ist besonders wichtig, falls das Repo später öffentlich wird.

**Ports:** Postgres und Ollama sind nur auf `127.0.0.1` gebunden, aus dem LAN
erreichbar ist nur die App auf Port 8080.

## Clustering

Inkrementell, je Artikel in zeitlicher Reihenfolge:

1. Ähnlichste Story im Zeitfenster (±48 h um den Artikelzeitpunkt) per
   Cosine-Distanz `<=>` suchen.
2. Ähnlichkeit ≥ Schwellwert (0.70, kalibriert mit echten Daten) → zuordnen, Centroid als laufender
   Mittelwert nachführen.
3. Sonst neue Story.

Bewusst **ohne HNSW-Index**: Das Zeitfenster hält die Kandidaten klein, und eine
exakte Suche ist reproduzierbar. Gleiche Daten ergeben gleiche Stories.

**Tuning:** Der Schwellwert ist der wichtigste Regler. Zu tief, und Stories fressen
verwandte Themen (Drift des Centroids). Zu hoch, und dasselbe Ereignis zerfällt
in mehrere Stories. Am besten nach einem Tag Daten mit
`select similarity from article where similarity < 1 order by similarity` die
Verteilung anschauen.

## Quellen

`backend/src/main/resources/sources.yml`. Beim Start werden neue Codes und Feeds
angelegt, bestehende aktualisiert und entfernte deaktiviert (nicht gelöscht).

- Eine **Quelle** ist eine Redaktion, sie kann mehrere **Feeds** haben (`feed-urls`),
  z.B. World und Startseite. Doppelte Artikel werden über die URL erkannt.
- Meinungsseiten als eigene Quelle mit `section: OPINION` führen.
- `camp` ist für dein eigenes Lager-Tag reserviert.
- Ungültige Einträge (doppelter Code, Quelle ohne Feed, URL bei zwei Quellen)
  brechen den Start mit klarer Meldung ab.

Fehler stehen pro Feed im Log und in `source_feed.last_error`.

## SQL und pgvector

Alle Statements stehen als Text-Blöcke (`static final String`) oben in der
jeweiligen Klasse. Was an die DB geht, steht wortwörtlich im Code.

pgvector ohne Zusatzbibliothek: Vektoren gehen als Text `[0.1,0.2,...]` mit
`cast(:v as vector)` hinein und als `::text` heraus (`db/VectorText`).
Postgres-Enums werden als String-Literal (`status = 'NEW'`) oder mit
`cast(:section as source_section)` geschrieben.

## Tests

`mvn test` (in `backend/`): UrlNormalizer, Vektor-Textformat, Centroid-Mathematik,
Validierung von sources.yml. Integrationstests mit Testcontainers sind der nächste
sinnvolle Schritt.

**CI:** Jeder Pull Request wird auf einem GitHub-eigenen Runner gebaut und getestet
(`.github/workflows/ci.yml`), inklusive Prüfung der Compose-Datei. Der NUC ist daran
nicht beteiligt; deployt wird erst beim Merge auf `main`.

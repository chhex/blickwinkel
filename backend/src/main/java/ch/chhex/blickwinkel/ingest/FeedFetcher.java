package ch.chhex.blickwinkel.ingest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import ch.chhex.blickwinkel.config.BlickwinkelProperties;

/** Lädt einen RSS-/Atom-Feed und liefert bereinigte Einträge (HTML entfernt, gekürzt). */
@Component
public class FeedFetcher {

    /** Feeds mit Datum in der Zukunft gibt es; mehr als das tolerieren wir nicht. */
    private static final Duration MAX_CLOCK_SKEW = Duration.ofHours(1);

    private final HttpClient http;
    private final Duration timeout;
    private final String userAgent;
    private final int teaserMaxLength;

    public FeedFetcher(BlickwinkelProperties props) {
        var ingest = props.ingest();
        this.timeout = ingest.timeout();
        this.userAgent = ingest.userAgent();
        this.teaserMaxLength = ingest.teaserMaxLength();
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public List<FeedItem> fetch(URI feedUrl) throws IOException, InterruptedException, FeedException {
        HttpRequest request = HttpRequest.newBuilder(feedUrl)
                .timeout(timeout)
                .header("User-Agent", userAgent)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml;q=0.9, */*;q=0.5")
                .GET()
                .build();

        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() >= 300) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " für " + feedUrl);
        }

        SyndFeed feed;
        try (InputStream in = response.body(); XmlReader reader = new XmlReader(in)) {
            feed = new SyndFeedInput().build(reader);
        }

        Instant latestAllowed = Instant.now().plus(MAX_CLOCK_SKEW);
        List<FeedItem> items = new ArrayList<>(feed.getEntries().size());
        for (SyndEntry entry : feed.getEntries()) {
            String url = link(entry);
            String title = clean(entry.getTitle());
            if (url == null || title == null) {
                continue;
            }
            items.add(new FeedItem(url, title, truncate(clean(teaserHtml(entry))), published(entry, latestAllowed)));
        }
        return items;
    }

    private static String link(SyndEntry entry) {
        String link = entry.getLink();
        if (isHttp(link)) {
            return link.trim();
        }
        String uri = entry.getUri();
        return isHttp(uri) ? uri.trim() : null;
    }

    private static boolean isHttp(String s) {
        return s != null && (s.startsWith("http://") || s.startsWith("https://"));
    }

    private static String teaserHtml(SyndEntry entry) {
        SyndContent description = entry.getDescription();
        if (description != null && description.getValue() != null && !description.getValue().isBlank()) {
            return description.getValue();
        }
        List<SyndContent> contents = entry.getContents();
        if (contents != null && !contents.isEmpty()) {
            return contents.get(0).getValue();
        }
        return null;
    }

    private static Instant published(SyndEntry entry, Instant latestAllowed) {
        Date date = entry.getPublishedDate() != null ? entry.getPublishedDate() : entry.getUpdatedDate();
        if (date == null) {
            return null;
        }
        Instant instant = date.toInstant();
        return instant.isAfter(latestAllowed) ? null : instant;
    }

    /** HTML und Entities entfernen, Whitespace zusammenziehen. Leer wird zu null. */
    private static String clean(String html) {
        if (html == null) {
            return null;
        }
        String text = Jsoup.parse(html).text().trim();
        return text.isEmpty() ? null : text;
    }

    private String truncate(String text) {
        if (text == null || text.length() <= teaserMaxLength) {
            return text;
        }
        return text.substring(0, teaserMaxLength).trim() + "…";
    }
}

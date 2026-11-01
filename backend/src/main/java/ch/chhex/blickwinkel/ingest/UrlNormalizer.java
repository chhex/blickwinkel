package ch.chhex.blickwinkel.ingest;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalisiert Artikel-URLs für die Deduplizierung: Schema und Host klein,
 * Fragment weg, Tracking-Parameter weg, restliche Parameter sortiert.
 */
public final class UrlNormalizer {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid",
            "ref", "ref_src", "cmpid", "ns_campaign", "ns_mchannel", "ns_source", "ns_linkname",
            "xtor", "at_medium", "at_campaign", "wt_mc", "ito");

    private UrlNormalizer() {
    }

    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL ist leer");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Ungültige URL: " + url, e);
        }
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalArgumentException("URL ohne Schema oder Host: " + url);
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = port == -1
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);

        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        String query = normalizeQuery(uri.getRawQuery());

        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (!defaultPort) {
            sb.append(':').append(port);
        }
        sb.append(path);
        if (!query.isEmpty()) {
            sb.append('?').append(query);
        }
        return sb.toString();
    }

    /** SHA-256 als 64 Hex-Zeichen, passend zu {@code article.url_hash char(64)}. */
    public static String hash(String normalizedUrl) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(normalizedUrl.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }

    private static String normalizeQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        return Arrays.stream(rawQuery.split("&"))
                .filter(p -> !p.isEmpty())
                .filter(p -> !isTracking(paramName(p)))
                .sorted()
                .collect(Collectors.joining("&"));
    }

    private static String paramName(String pair) {
        int eq = pair.indexOf('=');
        return (eq < 0 ? pair : pair.substring(0, eq)).toLowerCase(Locale.ROOT);
    }

    private static boolean isTracking(String name) {
        return name.startsWith("utm_") || TRACKING_PARAMS.contains(name);
    }
}

package de.dlr.shepard.plugins.references.dbpediadatabus.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.references.dbpediadatabus.io.DbpediaDatabusPreviewIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * REF1c — HTTP client for the DBpedia Databus. JDK-only (no
 * rest-client / mock-server deps). Fetches the artifact's JSON-LD,
 * optionally exchanges OAuth client-credentials for a bearer token,
 * parses the JSON-LD onto the {@link DbpediaDatabusPreviewIO} wire
 * shape. Retries once on 5xx / IOException with a 1s backoff.
 */
@ApplicationScoped
public class DatabusHttpClient {

  static final long TOKEN_REFRESH_MARGIN_SECONDS = 60L;
  static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
  static final Duration RETRY_BACKOFF = Duration.ofSeconds(1);
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private final HttpClient http;
  private final ObjectMapper mapper;
  private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

  public DatabusHttpClient() {
    this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(), new ObjectMapper());
  }

  DatabusHttpClient(HttpClient http, ObjectMapper mapper) {
    this.http = http;
    this.mapper = mapper;
  }

  public DbpediaDatabusPreviewIO fetchArtifact(String artifactUri, AuthMode auth) {
    DbpediaDatabusPreviewIO out = new DbpediaDatabusPreviewIO();
    if (artifactUri == null || artifactUri.isBlank()) {
      out.setAvailable(false);
      out.setReason("invalid-uri");
      return out;
    }
    String token = null;
    if (auth != null && auth.isOauthClientCredentials()) {
      try {
        token = obtainBearerToken(auth);
      } catch (DatabusAuthException ae) {
        Log.warnf("REF1c: OAuth client-credentials exchange failed (%s)", ae.getMessage());
        out.setAvailable(false);
        out.setReason("auth.failed");
        return out;
      }
    }
    HttpResponse<String> response;
    try {
      response = getWithRetry(artifactUri, token);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      Log.debugf("REF1c: artifact fetch IO failure: %s", e.getMessage());
      out.setAvailable(false);
      out.setReason("fetch-failed");
      return out;
    }
    int status = response.statusCode();
    if (status == 401 || status == 403) {
      out.setAvailable(false);
      out.setReason("auth.failed");
      return out;
    }
    if (status / 100 != 2) {
      out.setAvailable(false);
      out.setReason("fetch-failed");
      return out;
    }
    try {
      parseJsonLdInto(response.body(), out);
    } catch (IOException ioe) {
      Log.debugf("REF1c: JSON-LD parse failure: %s", ioe.getMessage());
      out.setAvailable(false);
      out.setReason("parse-failed");
      return out;
    }
    out.setAvailable(true);
    return out;
  }

  public ConnectionTestResult testConnection(String endpoint) {
    long start = System.nanoTime();
    try {
      HttpRequest req = HttpRequest
        .newBuilder(URI.create(endpoint))
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", "application/ld+json, application/json, */*;q=0.5")
        .GET()
        .build();
      HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
      long ms = (System.nanoTime() - start) / 1_000_000;
      return new ConnectionTestResult(true, res.statusCode(), ms, null);
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      long ms = (System.nanoTime() - start) / 1_000_000;
      return new ConnectionTestResult(false, null, ms, e.getClass().getSimpleName() + ": " + e.getMessage());
    } catch (IllegalArgumentException iae) {
      return new ConnectionTestResult(false, null, 0L, "invalid-endpoint: " + iae.getMessage());
    }
  }

  private HttpResponse<String> getWithRetry(String url, String bearerToken)
    throws IOException, InterruptedException {
    HttpRequest.Builder b = HttpRequest
      .newBuilder(URI.create(url))
      .timeout(REQUEST_TIMEOUT)
      .header("Accept", "application/ld+json, application/json")
      .GET();
    if (bearerToken != null && !bearerToken.isBlank()) {
      b.header("Authorization", "Bearer " + bearerToken);
    }
    HttpRequest req = b.build();
    try {
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (res.statusCode() / 100 == 5) {
        Thread.sleep(RETRY_BACKOFF.toMillis());
        return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      }
      return res;
    } catch (IOException ioe) {
      Thread.sleep(RETRY_BACKOFF.toMillis());
      return http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
  }

  private String obtainBearerToken(AuthMode auth) throws DatabusAuthException {
    long now = System.currentTimeMillis() / 1000L;
    CachedToken cached = cachedToken.get();
    if (cached != null && cached.expiresAtSeconds > now + TOKEN_REFRESH_MARGIN_SECONDS) {
      return cached.bearer;
    }
    if (auth.tokenUrl == null || auth.tokenUrl.isBlank() || auth.clientId == null || auth.clientId.isBlank()) {
      throw new DatabusAuthException("OAuth wiring incomplete (tokenUrl or clientId missing)");
    }
    String body =
      "grant_type=client_credentials&client_id=" +
      enc(auth.clientId) +
      "&client_secret=" +
      enc(auth.clientSecret == null ? "" : auth.clientSecret);
    HttpRequest req = HttpRequest
      .newBuilder(URI.create(auth.tokenUrl))
      .timeout(REQUEST_TIMEOUT)
      .header("Content-Type", "application/x-www-form-urlencoded")
      .header("Accept", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
      .build();
    HttpResponse<String> res;
    try {
      res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new DatabusAuthException("token-exchange-io: " + e.getMessage());
    }
    if (res.statusCode() / 100 != 2) {
      throw new DatabusAuthException("token-exchange status " + res.statusCode());
    }
    try {
      JsonNode root = mapper.readTree(res.body());
      String access = root.path("access_token").asText(null);
      long expiresIn = root.path("expires_in").asLong(3600L);
      if (access == null || access.isBlank()) {
        throw new DatabusAuthException("token-exchange response missing access_token");
      }
      cachedToken.set(new CachedToken(access, now + expiresIn));
      return access;
    } catch (IOException ioe) {
      throw new DatabusAuthException("token-exchange parse: " + ioe.getMessage());
    }
  }

  private static String enc(String s) {
    return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  void parseJsonLdInto(String body, DbpediaDatabusPreviewIO out) throws IOException {
    JsonNode root = mapper.readTree(body);
    JsonNode artifact = pickArtifactNode(root);
    if (artifact == null || artifact.isMissingNode() || artifact.isNull()) {
      out.setTitle(null);
      return;
    }
    out.setTitle(firstString(artifact, "dcat:title", "dct:title", "title"));
    out.setDescription(firstString(artifact, "dct:abstract", "dct:description", "description"));
    out.setVersion(firstString(artifact, "dcat:version", "dct:hasVersion", "version"));
    out.setLicence(firstString(artifact, "dct:license", "dct:rights", "license"));
    String modified = firstString(artifact, "dct:modified", "dct:issued", "modified");
    if (modified != null) {
      try {
        out.setModifiedAt(Date.from(Instant.parse(modified)));
      } catch (DateTimeParseException dtpe) {
        try {
          out.setModifiedAt(Date.from(Instant.parse(modified + "T00:00:00Z")));
        } catch (DateTimeParseException ignored) {
          // give up — leave modifiedAt null.
        }
      }
    }
    out.setDistributions(parseDistributions(artifact));
  }

  private static JsonNode pickArtifactNode(JsonNode root) {
    if (root == null || root.isMissingNode() || root.isNull()) return null;
    if (root.isObject() && root.has("@graph") && root.get("@graph").isArray() && root.get("@graph").size() > 0) {
      for (JsonNode n : root.get("@graph")) {
        if (looksLikeArtifact(n)) return n;
      }
      return root.get("@graph").get(0);
    }
    if (root.isArray()) {
      if (root.size() == 0) return null;
      for (JsonNode n : root) {
        if (looksLikeArtifact(n)) return n;
      }
      return root.get(0);
    }
    return root;
  }

  private static boolean looksLikeArtifact(JsonNode n) {
    if (n == null || !n.isObject()) return false;
    JsonNode type = n.path("@type");
    if (type.isMissingNode()) return false;
    if (type.isTextual()) {
      String t = type.asText("").toLowerCase(Locale.ROOT);
      return t.contains("artifact") || t.contains("dataset") || t.contains("version");
    }
    if (type.isArray()) {
      for (JsonNode t : type) {
        String s = t.asText("").toLowerCase(Locale.ROOT);
        if (s.contains("artifact") || s.contains("dataset") || s.contains("version")) return true;
      }
    }
    return false;
  }

  private static String firstString(JsonNode node, String... keys) {
    for (String k : keys) {
      JsonNode v = node.get(k);
      if (v == null || v.isMissingNode() || v.isNull()) continue;
      if (v.isTextual()) {
        String t = v.asText();
        if (!t.isBlank()) return t;
      }
      if (v.isArray()) {
        for (JsonNode e : v) {
          if (e.isObject() && e.has("@value")) {
            String t = e.get("@value").asText("");
            if (!t.isBlank()) return t;
          } else if (e.isTextual()) {
            String t = e.asText();
            if (!t.isBlank()) return t;
          } else if (e.isObject() && e.has("@id")) {
            String t = e.get("@id").asText();
            if (!t.isBlank()) return t;
          }
        }
      }
      if (v.isObject()) {
        if (v.has("@value")) {
          String t = v.get("@value").asText("");
          if (!t.isBlank()) return t;
        }
        if (v.has("@id")) {
          String t = v.get("@id").asText("");
          if (!t.isBlank()) return t;
        }
      }
    }
    return null;
  }

  private static List<DbpediaDatabusPreviewIO.Distribution> parseDistributions(JsonNode artifact) {
    JsonNode dist = artifact.get("dcat:distribution");
    if (dist == null || dist.isMissingNode() || dist.isNull()) dist = artifact.get("distribution");
    if (dist == null || dist.isMissingNode() || dist.isNull()) return null;
    List<DbpediaDatabusPreviewIO.Distribution> out = new ArrayList<>();
    if (dist.isArray()) {
      for (JsonNode d : dist) out.add(parseDistribution(d));
    } else if (dist.isObject()) {
      out.add(parseDistribution(dist));
    }
    return out.isEmpty() ? null : out;
  }

  private static DbpediaDatabusPreviewIO.Distribution parseDistribution(JsonNode d) {
    DbpediaDatabusPreviewIO.Distribution out = new DbpediaDatabusPreviewIO.Distribution();
    out.setName(firstString(d, "dct:title", "title", "dcat:title"));
    out.setMimeType(firstString(d, "dcat:mediaType", "dct:format", "format", "mediaType"));
    out.setDownloadUrl(firstString(d, "dcat:downloadURL", "downloadURL", "@id"));
    JsonNode size = d.get("dcat:byteSize");
    if (size == null || size.isMissingNode()) size = d.get("byteSize");
    if (size != null && !size.isMissingNode() && !size.isNull()) {
      if (size.isNumber()) {
        out.setByteSize(size.asLong());
      } else if (size.isTextual()) {
        try {
          out.setByteSize(Long.parseLong(size.asText().trim()));
        } catch (NumberFormatException ignored) {
          String t = size.asText().trim();
          StringBuilder b = new StringBuilder();
          for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) b.append(c);
            else break;
          }
          if (b.length() > 0) {
            try {
              out.setByteSize(Long.parseLong(b.toString()));
            } catch (NumberFormatException nfe) {
              // give up.
            }
          }
        }
      } else if (size.isObject() && size.has("@value")) {
        try {
          out.setByteSize(Long.parseLong(size.get("@value").asText().trim()));
        } catch (NumberFormatException ignored) {
          // give up.
        }
      }
    }
    return out;
  }

  // ─── DTOs ─────────────────────────────────────────────────────────

  public static final class AuthMode {

    final boolean oauth;
    final String tokenUrl;
    final String clientId;
    final String clientSecret;

    private AuthMode(boolean oauth, String tokenUrl, String clientId, String clientSecret) {
      this.oauth = oauth;
      this.tokenUrl = tokenUrl;
      this.clientId = clientId;
      this.clientSecret = clientSecret;
    }

    public static AuthMode none() {
      return new AuthMode(false, null, null, null);
    }

    public static AuthMode oauthClientCredentials(String tokenUrl, String clientId, String clientSecret) {
      return new AuthMode(true, tokenUrl, clientId, clientSecret);
    }

    boolean isOauthClientCredentials() {
      return oauth;
    }
  }

  static final class CachedToken {

    final String bearer;
    final long expiresAtSeconds;

    CachedToken(String bearer, long expiresAtSeconds) {
      this.bearer = bearer;
      this.expiresAtSeconds = expiresAtSeconds;
    }
  }

  public void clearTokenCache() {
    cachedToken.set(null);
  }

  public record ConnectionTestResult(boolean reachable, Integer statusCode, Long latencyMs, String reason) {}

  static final class DatabusAuthException extends Exception {

    DatabusAuthException(String message) {
      super(message);
    }
  }
}

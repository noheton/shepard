package de.dlr.shepard.context.semantic;

import de.dlr.shepard.context.semantic.OntologySeedService.OntologyEntry;
import io.quarkus.logging.Log;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.neo4j.ogm.session.Session;

/**
 * N1c — operator-triggered refresh of the bundled ontologies against
 * each bundle's pinned canonical URL.
 *
 * <p>The {@link OntologySeedService} pre-seed pass on startup imports
 * the SHA-256-pinned classpath stubs (minimum-viable Turtle, ~16 KB
 * total). N1c lets an admin upgrade those stubs in place at runtime
 * without waiting for the next shepard release — the service:
 *
 * <ol>
 *   <li>Reads the manifest via
 *       {@link OntologySeedService#loadManifest()}.</li>
 *   <li>For each bundle in the request (default = all), fetches the
 *       canonical Turtle/RDF from {@code canonicalUrl}, computes the
 *       SHA-256 of the response body, and compares it against the
 *       bundled stub's SHA-256.</li>
 *   <li>When the hash differs (or {@code force=true}), calls
 *       {@code n10s.rdf.import.inline} on the OGM session.</li>
 *   <li>When the hash matches and {@code force=false}, increments the
 *       "already current" counter and moves on.</li>
 * </ol>
 *
 * <p><b>Fail-soft per bundle.</b> A bundle that can't be fetched, has
 * no {@code canonicalUrl}, fails to parse, or comes back from n10s
 * with a non-OK status is recorded in the {@code errors} list with an
 * operator-readable reason; the next bundle is then attempted. The
 * service never throws on per-bundle errors — only on input
 * validation (unknown bundle id in the request).
 *
 * <p>This mirrors {@link OntologySeedService}'s
 * <em>convenience-not-correctness-gate</em> stance from {@code aidocs/48}:
 * the internal repo is a casual-user enabler, not load-bearing
 * infrastructure. A failed refresh leaves the previously-imported
 * stubs intact.
 *
 * @see OntologySeedService
 * @see de.dlr.shepard.v2.admin.semantic.SemanticAdminRest
 */
public class OntologyRefreshService {

  /** Default per-bundle HTTP-fetch timeout. Big-enough for QUDT (~5 MB). */
  static final Duration FETCH_TIMEOUT = Duration.ofSeconds(60);

  /** Connect-phase timeout — separate so a slow DNS doesn't blow the whole bundle budget. */
  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

  private final Session session;
  private final HttpClient httpClient;
  private final OntologySeedService delegate;

  /** Production ctor — pulls the OGM session at call time. */
  public OntologyRefreshService() {
    this(
      de.dlr.shepard.common.neo4j.NeoConnector.getInstance().getNeo4jSession(),
      HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
      new OntologySeedService()
    );
  }

  /** Test seam — accept session + http client + delegate seed service. */
  public OntologyRefreshService(Session session, HttpClient httpClient, OntologySeedService delegate) {
    this.session = session;
    this.httpClient = httpClient;
    this.delegate = delegate;
  }

  /**
   * Refresh the requested bundles (or all of them when
   * {@code requestedIds} is empty / null). Best-effort per bundle —
   * collect errors and keep going.
   */
  public RefreshOutcome refresh(Collection<String> requestedIds, boolean force) {
    final List<OntologyEntry> manifest;
    try {
      manifest = delegate.loadManifest();
    } catch (RuntimeException ex) {
      // Manifest load failure is a hard error — nothing the operator
      // can do per-bundle. Translate to a single result-level error
      // and an empty success count.
      RefreshOutcome out = new RefreshOutcome();
      out.errors.add(new BundleError("<manifest>", "Failed to load ontologies manifest: " + ex.getMessage()));
      return out;
    }

    // Translate ids → entries; capture unknown ids as up-front errors.
    final List<OntologyEntry> targets = new ArrayList<>();
    final List<BundleError> upFrontErrors = new ArrayList<>();
    if (requestedIds == null || requestedIds.isEmpty()) {
      targets.addAll(manifest);
    } else {
      Map<String, OntologyEntry> byId = new java.util.LinkedHashMap<>();
      for (OntologyEntry e : manifest) byId.put(e.id, e);
      Set<String> seen = new java.util.LinkedHashSet<>();
      for (String raw : requestedIds) {
        if (raw == null) continue;
        String id = raw.trim();
        if (id.isEmpty()) continue;
        if (!seen.add(id)) continue; // dedupe
        OntologyEntry e = byId.get(id);
        if (e == null) {
          upFrontErrors.add(new BundleError(id, "Unknown bundle id — not present in ontologies-manifest.json."));
          continue;
        }
        targets.add(e);
      }
    }

    int refreshed = 0;
    int alreadyCurrent = 0;
    List<BundleError> errors = new ArrayList<>(upFrontErrors);
    for (OntologyEntry entry : targets) {
      try {
        BundleOutcome outcome = refreshOne(entry, force);
        if (outcome == BundleOutcome.REFRESHED) refreshed++;
        else if (outcome == BundleOutcome.ALREADY_CURRENT) alreadyCurrent++;
      } catch (RuntimeException ex) {
        String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        Log.warnf(
          "OntologyRefreshService: bundle '%s' failed (%s); continuing with next.",
          entry.id,
          reason
        );
        errors.add(new BundleError(entry.id, reason));
      }
    }

    RefreshOutcome out = new RefreshOutcome();
    out.requested = targets.size() + upFrontErrors.size();
    out.refreshed = refreshed;
    out.alreadyCurrent = alreadyCurrent;
    out.errors = errors;
    Log.infof(
      "OntologyRefreshService: refresh complete — requested=%d refreshed=%d alreadyCurrent=%d errors=%d",
      out.requested,
      out.refreshed,
      out.alreadyCurrent,
      out.errors.size()
    );
    return out;
  }

  /** Refresh a single bundle: fetch → compare hash → import if differing. */
  BundleOutcome refreshOne(OntologyEntry entry, boolean force) {
    if (entry.canonicalUrl == null || entry.canonicalUrl.isBlank()) {
      throw new IllegalStateException("Manifest entry has no canonicalUrl; cannot refresh.");
    }
    byte[] body = fetchCanonical(entry.canonicalUrl);
    String fetchedSha = OntologySeedService.sha256Hex(body);
    if (!force && fetchedSha.equalsIgnoreCase(entry.sha256)) {
      Log.infof(
        "OntologyRefreshService: bundle '%s' already current (sha256=%s, %d bytes); skipping import.",
        entry.id,
        fetchedSha,
        body.length
      );
      return BundleOutcome.ALREADY_CURRENT;
    }
    String rdf = new String(body, StandardCharsets.UTF_8);
    Map<String, Object> result = invokeImport(rdf, entry.format);
    Object status = result.get("status");
    Object loaded = result.get("loaded");
    Log.infof(
      "OntologyRefreshService: bundle '%s' refreshed — status=%s triplesLoaded=%s (sha256 was %s, now %s).",
      entry.id,
      status,
      loaded,
      entry.sha256,
      fetchedSha
    );
    if (status != null && !"OK".equalsIgnoreCase(status.toString())) {
      throw new IllegalStateException("n10s returned non-OK status '" + status + "' for bundle '" + entry.id + "'.");
    }
    return BundleOutcome.REFRESHED;
  }

  /** Fetch the canonical URL as raw bytes. Visible-for-tests. */
  byte[] fetchCanonical(String url) {
    final URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Malformed canonicalUrl '" + url + "': " + ex.getMessage(), ex);
    }
    HttpRequest request = HttpRequest.newBuilder(uri)
      .GET()
      .timeout(FETCH_TIMEOUT)
      // Most canonical hosts content-negotiate; ask for Turtle but
      // accept anything an n10s parser can ingest. The OWL/RDF-XML
      // bundles (RO, FOAF) explicitly land at .owl / .rdf URLs.
      .header("Accept", "text/turtle, application/rdf+xml;q=0.8, application/owl+xml;q=0.7, */*;q=0.1")
      .header("User-Agent", "shepard-admin-refresh-ontologies/1.0")
      .build();
    final HttpResponse<byte[]> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException ex) {
      throw new IllegalStateException("Could not fetch " + url + ": " + ex.getMessage(), ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while fetching " + url, ex);
    }
    int status = response.statusCode();
    if (status < 200 || status >= 300) {
      throw new IllegalStateException("HTTP " + status + " fetching " + url);
    }
    byte[] body = response.body();
    if (body == null || body.length == 0) {
      throw new IllegalStateException("Empty body fetching " + url);
    }
    return body;
  }

  /** Invoke {@code n10s.rdf.import.inline}. Mirrors the seed-service helper. */
  Map<String, Object> invokeImport(String rdf, String format) {
    var result = session.query(
      OntologySeedService.IMPORT_INLINE_CYPHER,
      Map.of("rdf", rdf, "format", format == null || format.isBlank() ? "Turtle" : format)
    );
    var it = result.queryResults().iterator();
    if (!it.hasNext()) return Collections.emptyMap();
    return new java.util.LinkedHashMap<>(it.next());
  }

  /** Per-bundle terminal outcome. Visible-for-tests. */
  enum BundleOutcome {
    REFRESHED,
    ALREADY_CURRENT,
  }

  /** Per-bundle error record. */
  public static final class BundleError {

    public final String bundle;
    public final String reason;

    public BundleError(String bundle, String reason) {
      this.bundle = bundle;
      this.reason = reason;
    }
  }

  /** Aggregate result of a refresh call. */
  public static final class RefreshOutcome {

    public int requested;
    public int refreshed;
    public int alreadyCurrent;
    public List<BundleError> errors = new ArrayList<>();
  }
}

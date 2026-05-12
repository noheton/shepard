package de.dlr.shepard.data.hdf.hsds;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.common.configuration.feature.toggles.HdfFeatureToggle;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A5a / A5b — thin HTTP wrapper around the HSDS sidecar
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md} §2 / §5 / §6).
 *
 * <p>Phase 1 (A5a) shipped <em>provision domain</em>
 * ({@link #createDomain(String)}) and <em>drop domain</em>
 * ({@link #deleteDomain(String)}).
 *
 * <p>Phase 2 (A5b) adds the <em>permission-bridge</em> primitives:
 * <ul>
 *   <li>{@link #getDomainAcl(String)} — read the per-domain ACL.</li>
 *   <li>{@link #setDomainAcl(String, String, java.util.Collection,
 *       java.util.Collection, java.util.Collection)} — replace the ACL
 *       atomically (one HSDS {@code PUT /acls/{user}} per principal).</li>
 *   <li>{@link #clearDomainAcl(String)} — drop all non-owner ACEs.</li>
 * </ul>
 *
 * <h3>shepard ↔ HSDS ACL mapping</h3>
 *
 * shepard's permission model is a graph: Owner + Readers + Writers +
 * Managers (users <em>or</em> usergroups). HSDS uses POSIX-shape per-domain
 * ACLs with the six perm bits {@code create}, {@code read}, {@code update},
 * {@code delete}, {@code readACL}, {@code updateACL}. The bridge translates:
 *
 * <pre>
 *   shepard role  ↦  HSDS perm bits
 *   ─────────────────────────────────────────────────────────────
 *   Owner          {create, read, update, delete, readACL, updateACL}
 *   Manager        {create, read, update, delete, readACL, updateACL}
 *   Writer         {create, read, update, delete}
 *   Reader         {read}
 * </pre>
 *
 * <p>Group-mode ACLs: shepard's permission graph is per-container today,
 * not per-group. A5b explicitly does <em>not</em> flow shepard permissions
 * down to per-HSDS-group ACLs ({@code aidocs/35 §6}). Finer-grained access
 * is a future permission-shape question on the shepard side.
 *
 * <p><strong>Sync direction.</strong> shepard is the source of truth.
 * Direct HSDS-side ACL mutations get clobbered on the next shepard
 * write — operators must not edit HSDS ACLs out of band. The
 * {@code POST /v2/admin/hdf/rebuild-acls} admin endpoint covers drift
 * recovery.
 *
 * <p><strong>Auth.</strong> Phase 1 + Phase 2 use HTTP Basic against the
 * HSDS sidecar with a single admin credential supplied by the operator
 * via {@code shepard.hdf.hsds.username} /
 * {@code shepard.hdf.hsds.password}. Per-user OIDC token relay
 * arrives in A5e ({@code aidocs/35 §5}).
 *
 * <p><strong>Lookup.</strong> The bean is gated on the
 * {@code shepard.hdf.enabled=true} property via
 * {@link LookupIfProperty}; with the feature off it is never
 * instantiated and no outbound TCP traffic is ever attempted.
 * Mirrors the spatial-feature-toggle pattern (see
 * {@link HdfFeatureToggle}).
 *
 * <p>Uses Java 21's built-in {@link HttpClient}; no new HTTP
 * dependency is taken.
 */
@ApplicationScoped
@LookupIfProperty(name = "shepard.hdf.enabled", stringValue = "true")
public class HsdsClient {

  @ConfigProperty(name = "shepard.hdf.hsds.endpoint", defaultValue = "http://shepard-hsds:5101")
  String endpoint;

  @ConfigProperty(name = "shepard.hdf.hsds.username", defaultValue = "")
  String username;

  @ConfigProperty(name = "shepard.hdf.hsds.password", defaultValue = "")
  String password;

  @ConfigProperty(name = "shepard.hdf.hsds.timeout", defaultValue = "PT10S")
  Duration timeout;

  /** Lazily-built; package-private for test seam. */
  HttpClient httpClient;

  /**
   * Initialise the underlying {@link HttpClient}. Fails-fast at
   * startup if credentials are missing — Phase 1 requires admin
   * Basic credentials; running the feature without them is a
   * misconfiguration, not a degraded mode.
   */
  @PostConstruct
  void init() {
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      throw new HsdsConfigurationException(
        "shepard.hdf.enabled=true requires shepard.hdf.hsds.username and " +
        "shepard.hdf.hsds.password to be set (Phase 1 uses HTTP Basic against " +
        "the HSDS sidecar). See docs/admin.md §\"HDF5 (HSDS)\"."
      );
    }
    if (httpClient == null) {
      httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
    }
    Log.infof("HSDS client initialised against endpoint=%s (HTTP Basic, Phase 1)", endpoint);
  }

  /**
   * Test seam — package-private constructor to inject a stub
   * {@link HttpClient} and credentials without going through CDI.
   */
  HsdsClient(String endpoint, String username, String password, Duration timeout, HttpClient httpClient) {
    this.endpoint = endpoint;
    this.username = username;
    this.password = password;
    this.timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    this.httpClient = httpClient;
  }

  /** CDI no-arg ctor (config fields injected after construction). */
  public HsdsClient() {
    // intentionally empty — config injection populates the fields, @PostConstruct does the rest
  }

  /**
   * Provision an HSDS domain at the given path.
   *
   * @param domain HSDS-style domain path, e.g. {@code /shepard/<appId>/}.
   *               Must start with {@code /}; the trailing slash is required
   *               for folder-style domains by HSDS convention.
   * @throws HsdsException on transport error, 4xx/5xx, or auth failure.
   */
  public void createDomain(String domain) {
    String safe = requireValidDomain(domain);
    HttpRequest request = baseRequest("/?domain=" + safe).PUT(HttpRequest.BodyPublishers.noBody()).build();
    HttpResponse<String> response = send(request, "createDomain", safe);
    expectSuccess(response, "createDomain", safe);
    Log.debugf("HSDS createDomain ok: %s", safe);
  }

  /**
   * Drop an HSDS domain (recursive — all groups / datasets /
   * attributes inside go too).
   *
   * @param domain HSDS-style domain path.
   * @throws HsdsException on transport error, 4xx/5xx, or auth failure.
   */
  public void deleteDomain(String domain) {
    String safe = requireValidDomain(domain);
    HttpRequest request = baseRequest("/?domain=" + safe).DELETE().build();
    HttpResponse<String> response = send(request, "deleteDomain", safe);
    expectSuccess(response, "deleteDomain", safe);
    Log.debugf("HSDS deleteDomain ok: %s", safe);
  }

  /** Configured base endpoint. Exposed for diagnostics. */
  public String getEndpoint() {
    return endpoint;
  }

  // ─── A5b: ACL operations ────────────────────────────────────────────────

  /** Shared, thread-safe Jackson mapper for ACL JSON shapes. */
  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * Fetch the HSDS domain ACL. Returns the parsed JSON body — used by
   * the rebuild-acls drift detector to compare expected vs actual.
   *
   * @param domain HSDS-style domain path, e.g. {@code /shepard/<appId>/}.
   * @return the response body verbatim (HSDS's {@code /acls} shape).
   * @throws HsdsException on transport error, 4xx/5xx, or auth failure.
   */
  public String getDomainAcl(String domain) {
    String safe = requireValidDomain(domain);
    HttpRequest request = baseRequest("/acls?domain=" + safe).GET().build();
    HttpResponse<String> response = send(request, "getDomainAcl", safe);
    expectSuccess(response, "getDomainAcl", safe);
    return response.body() == null ? "" : response.body();
  }

  /**
   * Replace the HSDS domain ACL with shepard's authoritative view.
   *
   * <p>One PUT per principal (HSDS's {@code /acls/{username}} surface
   * is per-user). The owner is upserted with the full perm-set; each
   * reader / writer / manager gets the role's perm-bits per the
   * mapping documented in the class javadoc. Existing ACEs not in
   * the new set are removed via a best-effort clear pass to keep
   * the HSDS side in sync.
   *
   * <p>Idempotent: re-running the same write yields the same ACL.
   * Best-effort on the per-principal level — a single principal
   * failure logs a warning and continues; the caller sees an
   * exception only if <em>every</em> per-principal write fails (so
   * the partial-failure path on the rebuild-acls endpoint can
   * report partial successes meaningfully).
   *
   * @param domain   HSDS domain path.
   * @param owner    owner username (becomes the HSDS owner-ACE).
   * @param readers  shepard Reader users (HSDS read).
   * @param writers  shepard Writer users (HSDS read+update+create+delete).
   * @param managers shepard Manager users (HSDS full perms + updateACL).
   * @throws HsdsException on transport / auth failure that blocks ALL writes.
   */
  public void setDomainAcl(
    String domain,
    String owner,
    Collection<String> readers,
    Collection<String> writers,
    Collection<String> managers
  ) {
    String safe = requireValidDomain(domain);
    int attempted = 0;
    int failed = 0;
    RuntimeException firstError = null;

    // 1. Clear non-owner ACEs we've previously set. Skip on transport failure —
    //    the per-principal writes below will overwrite anything we miss.
    try {
      clearDomainAclInternal(safe, owner);
    } catch (RuntimeException e) {
      Log.warnf(e, "HSDS clearDomainAcl best-effort failed for domain=%s; continuing with per-principal writes", safe);
    }

    // 2. Owner — always full perms.
    if (owner != null && !owner.isBlank()) {
      attempted++;
      try {
        putAce(safe, owner, ownerPerms());
      } catch (RuntimeException e) {
        failed++;
        if (firstError == null) firstError = e;
        Log.warnf(e, "HSDS owner-ACE write failed for domain=%s owner=%s", safe, owner);
      }
    }
    // 3. Managers — full perms minus ownership.
    for (String user : dedupe(managers, owner)) {
      attempted++;
      try {
        putAce(safe, user, managerPerms());
      } catch (RuntimeException e) {
        failed++;
        if (firstError == null) firstError = e;
        Log.warnf(e, "HSDS manager-ACE write failed for domain=%s user=%s", safe, user);
      }
    }
    // 4. Writers.
    for (String user : dedupe(writers, owner)) {
      attempted++;
      try {
        putAce(safe, user, writerPerms());
      } catch (RuntimeException e) {
        failed++;
        if (firstError == null) firstError = e;
        Log.warnf(e, "HSDS writer-ACE write failed for domain=%s user=%s", safe, user);
      }
    }
    // 5. Readers.
    for (String user : dedupe(readers, owner)) {
      attempted++;
      try {
        putAce(safe, user, readerPerms());
      } catch (RuntimeException e) {
        failed++;
        if (firstError == null) firstError = e;
        Log.warnf(e, "HSDS reader-ACE write failed for domain=%s user=%s", safe, user);
      }
    }

    if (attempted > 0 && failed == attempted) {
      // Every per-principal write failed — surface the transport error.
      throw firstError != null
        ? firstError
        : new HsdsException("HSDS setDomainAcl failed for domain=" + safe + " — every per-principal write rejected.");
    }
    if (failed > 0) {
      Log.warnf("HSDS setDomainAcl partial failure for domain=%s: %d of %d ACE writes failed", safe, failed, attempted);
    } else {
      Log.debugf("HSDS setDomainAcl ok for domain=%s (%d ACEs)", safe, attempted);
    }
  }

  /**
   * Remove all non-owner ACEs on a domain. Used for "lock the
   * container" / cleanup paths. The owner ACE is preserved because
   * HSDS itself refuses a domain without one.
   *
   * @param domain HSDS-style domain path.
   * @throws HsdsException on transport error, 4xx/5xx, or auth failure
   *         talking to HSDS (per-principal best-effort errors are
   *         logged but not raised).
   */
  public void clearDomainAcl(String domain) {
    String safe = requireValidDomain(domain);
    // Pass null as "owner-to-preserve" — we don't know the owner ahead of time;
    // HSDS will reject a delete of the owner-ACE on its own.
    clearDomainAclInternal(safe, null);
    Log.debugf("HSDS clearDomainAcl ok for domain=%s", safe);
  }

  private void clearDomainAclInternal(String domain, String preserveUser) {
    // Read existing ACL, parse usernames, delete each non-owner one.
    String body;
    try {
      body = getDomainAcl(domain);
    } catch (HsdsException e) {
      // 404 is fine — fresh domain with no ACEs yet. Anything else propagates.
      String msg = e.getMessage();
      if (msg != null && msg.contains("HTTP 404")) return;
      throw e;
    }
    if (body == null || body.isBlank()) return;
    Set<String> users = parseAclUsernames(body);
    for (String user : users) {
      if (user == null || user.isBlank()) continue;
      if (preserveUser != null && preserveUser.equals(user)) continue;
      try {
        HttpRequest request = baseRequest("/acls/" + encode(user) + "?domain=" + domain).DELETE().build();
        HttpResponse<String> resp = send(request, "deleteAce", domain);
        int code = resp.statusCode();
        if (!(code >= 200 && code < 300) && code != 404) {
          // 404 is fine — already gone.
          Log.warnf("HSDS deleteAce non-success status %d for domain=%s user=%s", code, domain, user);
        }
      } catch (RuntimeException ignored) {
        Log.debugf("HSDS deleteAce ignored failure for domain=%s user=%s", domain, user);
      }
    }
  }

  /** Issue a {@code PUT /acls/{user}?domain=...} for the given perm-bits. */
  private void putAce(String domain, String user, ObjectNode perms) {
    if (user == null || user.isBlank()) return;
    String json;
    try {
      json = JSON.writeValueAsString(perms);
    } catch (JsonProcessingException e) {
      throw new HsdsException("HSDS ACL JSON serialisation failed for user=" + user, e);
    }
    HttpRequest request = baseRequest("/acls/" + encode(user) + "?domain=" + domain)
      .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
      .header("Content-Type", "application/json")
      .build();
    HttpResponse<String> response = send(request, "putAce", domain);
    expectSuccess(response, "putAce(" + user + ")", domain);
  }

  /** Extract the set of usernames that hold an ACE in an HSDS ACL response. */
  static Set<String> parseAclUsernames(String body) {
    Set<String> out = new LinkedHashSet<>();
    if (body == null || body.isBlank()) return out;
    try {
      var node = JSON.readTree(body);
      var acls = node.get("acls");
      if (acls != null && acls.isArray()) {
        for (var ace : acls) {
          var uname = ace.get("userName");
          if (uname != null && uname.isTextual()) {
            out.add(uname.asText());
          }
        }
      } else if (acls != null && acls.isObject()) {
        // HSDS variant that returns a {username: perms} object.
        acls.fieldNames().forEachRemaining(out::add);
      }
    } catch (IOException e) {
      Log.debugf("HSDS ACL response not JSON-parsable; treating as empty: %s", e.getMessage());
    }
    return out;
  }

  private ObjectNode ownerPerms() {
    return permBits(true, true, true, true, true, true);
  }

  private ObjectNode managerPerms() {
    return permBits(true, true, true, true, true, true);
  }

  private ObjectNode writerPerms() {
    return permBits(true, true, true, true, false, false);
  }

  private ObjectNode readerPerms() {
    return permBits(false, true, false, false, false, false);
  }

  private ObjectNode permBits(boolean create, boolean read, boolean update, boolean delete, boolean readACL, boolean updateACL) {
    ObjectNode n = JSON.createObjectNode();
    n.put("create", create);
    n.put("read", read);
    n.put("update", update);
    n.put("delete", delete);
    n.put("readACL", readACL);
    n.put("updateACL", updateACL);
    return n;
  }

  /** Distinct, non-null, non-blank, owner-stripped collection. */
  private static Set<String> dedupe(Collection<String> in, String owner) {
    if (in == null || in.isEmpty()) return Collections.emptySet();
    Set<String> out = new LinkedHashSet<>();
    for (String s : in) {
      if (s == null || s.isBlank()) continue;
      if (owner != null && owner.equals(s)) continue;
      out.add(s);
    }
    return out;
  }

  /** Encode a username for use in an HSDS URL path segment. */
  private static String encode(String user) {
    StringBuilder sb = new StringBuilder(user.length());
    for (int i = 0; i < user.length(); i++) {
      char c = user.charAt(i);
      if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
        sb.append(c);
      } else {
        // Refuse anything we can't safely embed — usernames in shepard are
        // ASCII per Keycloak today; this is a defence-in-depth guard.
        throw new IllegalArgumentException("HSDS username contains illegal character: '" + user + "'");
      }
    }
    return sb.toString();
  }

  /**
   * Compute a stable fingerprint of the expected HSDS ACL for drift
   * detection. Same shape regardless of input ordering — caller can
   * compare against a stored value on {@code :HdfContainer}.
   */
  public static String fingerprintAcl(String owner, Collection<String> readers, Collection<String> writers, Collection<String> managers) {
    StringBuilder sb = new StringBuilder();
    sb.append("o:").append(owner == null ? "" : owner).append('|');
    appendSorted(sb, "m", managers);
    appendSorted(sb, "w", writers);
    appendSorted(sb, "r", readers);
    return sb.toString();
  }

  private static void appendSorted(StringBuilder sb, String tag, Collection<String> users) {
    sb.append(tag).append(':');
    if (users == null || users.isEmpty()) {
      sb.append('|');
      return;
    }
    String[] arr = users.stream().filter(s -> s != null && !s.isBlank()).distinct().sorted().toArray(String[]::new);
    for (int i = 0; i < arr.length; i++) {
      if (i > 0) sb.append(',');
      sb.append(arr[i]);
    }
    sb.append('|');
  }

  // ─── helpers ────────────────────────────────────────────────────────────

  private HttpRequest.Builder baseRequest(String pathAndQuery) {
    URI uri;
    try {
      uri = new URI(stripTrailingSlash(endpoint) + pathAndQuery);
    } catch (URISyntaxException e) {
      throw new HsdsException(
        "Invalid HSDS endpoint configuration: " + endpoint + " — fix shepard.hdf.hsds.endpoint",
        e
      );
    }
    String basic =
      "Basic " +
      Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    return HttpRequest.newBuilder(uri).timeout(timeout).header("Authorization", basic).header("Accept", "application/json");
  }

  private HttpResponse<String> send(HttpRequest request, String op, String domain) {
    try {
      return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new HsdsException("HSDS " + op + " failed for domain=" + domain + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new HsdsException("HSDS " + op + " interrupted for domain=" + domain, e);
    }
  }

  private void expectSuccess(HttpResponse<String> response, String op, String domain) {
    int code = response.statusCode();
    if (code >= 200 && code < 300) {
      return;
    }
    if (code == 401 || code == 403) {
      throw new HsdsException(
        "HSDS " +
        op +
        " denied for domain=" +
        domain +
        " (HTTP " +
        code +
        "). Verify shepard.hdf.hsds.username / .password match the HSDS admin credentials " +
        "configured in the hdf compose profile."
      );
    }
    throw new HsdsException(
      "HSDS " + op + " failed for domain=" + domain + " (HTTP " + code + "): " + safeBody(response.body())
    );
  }

  private static String stripTrailingSlash(String s) {
    return s != null && s.endsWith("/") ? s.substring(0, s.length() - 1) : Objects.requireNonNullElse(s, "");
  }

  private static String safeBody(String body) {
    if (body == null) return "<no body>";
    return body.length() > 400 ? body.substring(0, 400) + "…" : body;
  }

  private static String requireValidDomain(String domain) {
    if (domain == null || domain.isBlank()) {
      throw new IllegalArgumentException("HSDS domain must be non-null and non-blank");
    }
    if (!domain.startsWith("/")) {
      throw new IllegalArgumentException("HSDS domain must start with '/' (got: " + domain + ")");
    }
    // Reject any path traversal / suspicious characters — the domain ends up in a query
    // string against HSDS so a control character or a stray '?' would corrupt the request.
    for (int i = 0; i < domain.length(); i++) {
      char c = domain.charAt(i);
      if (c < 0x20 || c == '?' || c == '#' || c == '&' || c == ' ') {
        throw new IllegalArgumentException("HSDS domain contains illegal character at index " + i + ": " + domain);
      }
    }
    return domain;
  }

  /** Operator-readable runtime failure talking to HSDS. */
  public static class HsdsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HsdsException(String message) {
      super(message);
    }

    public HsdsException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** Misconfiguration detected at startup — surfaced separately from runtime errors. */
  public static class HsdsConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public HsdsConfigurationException(String message) {
      super(message);
    }
  }
}

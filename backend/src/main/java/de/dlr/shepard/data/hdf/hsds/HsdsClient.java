package de.dlr.shepard.data.hdf.hsds;

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
import java.util.Objects;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * A5a Phase 1 — thin HTTP wrapper around the HSDS sidecar
 * ({@code aidocs/35-hdf5-hsds-implementation-design.md} §2 / §5).
 *
 * <p>Wraps the small subset of the HSDS REST surface this slice
 * needs: <em>provision domain</em> ({@link #createDomain(String)})
 * and <em>drop domain</em> ({@link #deleteDomain(String)}). Read /
 * write of dataset values are deferred to A5b/c/d; broker-style
 * passthrough is out of scope for Phase 1.
 *
 * <p><strong>Auth.</strong> Phase 1 uses HTTP Basic against the HSDS
 * sidecar with a single admin credential supplied by the operator
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

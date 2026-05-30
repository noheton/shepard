package de.dlr.shepard.v2.krl.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/**
 * KRL-INTERPRETER-05 — typed REST client for the KRL interpreter sidecar.
 *
 * <p>Mirrors the protocol fixed in
 * {@code aidocs/integrations/117-krl-interpreter.md §6}:
 * <pre>
 *   POST {baseUrl}/interpret  → 200 {trajectoryAppId, warnings, ikSolverStats, ...}
 *   GET  {baseUrl}/health     → 200 {status, version, ...}
 * </pre>
 *
 * <p>Uses {@link java.net.http.HttpClient} (no Quarkus REST Client
 * dependency) per the in-tree precedent in
 * {@code OntologyRefreshService} — keeps the dependency surface tight
 * and stays consistent with the SPI-neutral posture in
 * {@link de.dlr.shepard.spi.ai.Transport} ("the SPI doesn't constrain"
 * the HTTP client choice).
 *
 * <h2>Error model</h2>
 * <p>The client surfaces four discriminated outcomes via
 * {@link SidecarOutcome}:
 * <ul>
 *   <li>{@code OK} — 2xx with parsed JSON body.</li>
 *   <li>{@code SIDECAR_ERROR} — the sidecar responded with a 4xx or
 *       5xx; body is captured verbatim for the error envelope.</li>
 *   <li>{@code UNREACHABLE} — IO / connect failure; the sidecar is
 *       likely not running (operator opt-in).</li>
 *   <li>{@code TIMEOUT} — the call exceeded the configured deadline.</li>
 * </ul>
 *
 * <p>The service layer maps these to HTTP status codes per design
 * doc §7.3 (200→201, 5xx→502, timeout→504).
 *
 * <p><b>KRL-CONFIG-1:</b> sidecar URL and timeout are resolved via
 * {@link KrlInterpreterConfigService#effectiveSidecarUrl()} and
 * {@link KrlInterpreterConfigService#effectiveTimeoutSeconds()}.
 * Runtime values from the {@code :KrlInterpreterConfigEntity} singleton
 * win over the deploy-time bean ({@code shepard.krl.sidecar.*} keys),
 * per the "Always: surface operator knobs in the admin config" rule.
 */
@ApplicationScoped
public class KrlSidecarClient {

  @Inject KrlInterpreterConfigService configService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private HttpClient httpClient;

  @PostConstruct
  void init() {
    // Force HTTP/1.1 — the sidecar is FastAPI + uvicorn, which doesn't
    // negotiate HTTP/2 cleanly with Java's default upgrade attempt
    // ("Unsupported upgrade request" / "Invalid HTTP request received"
    // observed on first MFFD-rdk-urdf showcase smoke run 2026-05-30).
    this.httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  }

  /**
   * POST to {@code /interpret}. The {@code body} is serialised to JSON
   * via Jackson; the response body is parsed back as a generic
   * {@link Map} (the service layer pulls the discrete fields it cares
   * about — {@code trajectory}, {@code warnings}, …).
   *
   * <p>{@code .src} / {@code .urdf} / {@code .dat} bytes are passed
   * base64-encoded in the body alongside the appIds — see the design
   * doc §6 note in the PR description for the byte-passing extension.
   * The sidecar treats the appIds as opaque correlation handles; only
   * the bytes are used for parse / IK.
   *
   * @param body the request body — keys mirror the sidecar protocol §6
   * @return the discriminated outcome
   */
  public SidecarOutcome interpret(Map<String, Object> body) {
    String sidecarUrl = configService.effectiveSidecarUrl();
    URI uri;
    try {
      uri = URI.create(sidecarUrl + "/interpret");
    } catch (IllegalArgumentException ex) {
      Log.errorf("KRL: malformed sidecar URL %s", sidecarUrl);
      return SidecarOutcome.unreachable("Malformed sidecar URL: " + ex.getMessage());
    }
    return sendJson(uri, "POST", body);
  }

  /**
   * GET {@code /health}. Returns the parsed JSON body on 2xx,
   * otherwise the same discriminated outcomes as {@link #interpret}.
   */
  public SidecarOutcome health() {
    String sidecarUrl = configService.effectiveSidecarUrl();
    URI uri;
    try {
      uri = URI.create(sidecarUrl + "/health");
    } catch (IllegalArgumentException ex) {
      return SidecarOutcome.unreachable("Malformed sidecar URL: " + ex.getMessage());
    }
    return sendJson(uri, "GET", null);
  }

  /**
   * Visible-for-test seam — swap the underlying client for a stub in
   * unit tests so they don't open real sockets.
   */
  public void setHttpClientForTest(HttpClient client) {
    this.httpClient = client;
  }

  // ── internals ─────────────────────────────────────────────────────────────

  SidecarOutcome sendJson(URI uri, String method, Map<String, Object> body) {
    int timeoutSeconds = configService.effectiveTimeoutSeconds();
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofSeconds(timeoutSeconds))
      .header("Accept", "application/json")
      .header("User-Agent", "shepard-krl-interpreter-client/1.0");

    if ("GET".equals(method)) {
      builder.GET();
    } else {
      byte[] payload;
      try {
        payload = body == null ? new byte[0] : objectMapper.writeValueAsBytes(body);
      } catch (IOException ex) {
        return SidecarOutcome.unreachable("Failed to serialise request body: " + ex.getMessage());
      }
      builder
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
    }

    final HttpResponse<byte[]> response;
    try {
      response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    } catch (HttpTimeoutException ex) {
      Log.warnf("KRL: sidecar call timed out after %ds (url=%s)", timeoutSeconds, uri);
      return SidecarOutcome.timeout("Sidecar call timed out after " + timeoutSeconds + "s");
    } catch (IOException ex) {
      Log.warnf("KRL: sidecar unreachable (%s): %s", uri, ex.getMessage());
      return SidecarOutcome.unreachable(ex.getMessage());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      return SidecarOutcome.unreachable("Interrupted while calling sidecar");
    }

    int status = response.statusCode();
    byte[] bodyBytes = response.body();
    if (status < 200 || status >= 300) {
      String snippet = bodyBytes == null ? "" : new String(bodyBytes);
      if (snippet.length() > 512) snippet = snippet.substring(0, 512);
      Log.warnf("KRL: sidecar returned status=%d body=%s", status, snippet);
      return SidecarOutcome.sidecarError(status, snippet);
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> parsed = objectMapper.readValue(bodyBytes, Map.class);
      return SidecarOutcome.ok(parsed);
    } catch (IOException ex) {
      Log.warnf("KRL: failed to parse sidecar response: %s", ex.getMessage());
      return SidecarOutcome.sidecarError(500, "Sidecar returned non-JSON body");
    }
  }

  // ── result envelope ───────────────────────────────────────────────────────

  /**
   * Discriminated result of a sidecar call. The service layer maps
   * {@link #status} to the user-visible HTTP code per design §7.3.
   */
  public record SidecarOutcome(
    Status status,
    Map<String, Object> body,
    Integer sidecarStatus,
    String errorDetail
  ) {
    public enum Status { OK, SIDECAR_ERROR, UNREACHABLE, TIMEOUT }

    public static SidecarOutcome ok(Map<String, Object> body) {
      return new SidecarOutcome(Status.OK, body, 200, null);
    }

    public static SidecarOutcome sidecarError(int status, String detail) {
      return new SidecarOutcome(Status.SIDECAR_ERROR, null, status, detail);
    }

    public static SidecarOutcome unreachable(String detail) {
      return new SidecarOutcome(Status.UNREACHABLE, null, null, detail);
    }

    public static SidecarOutcome timeout(String detail) {
      return new SidecarOutcome(Status.TIMEOUT, null, null, detail);
    }

    public boolean isOk() {
      return status == Status.OK;
    }
  }
}

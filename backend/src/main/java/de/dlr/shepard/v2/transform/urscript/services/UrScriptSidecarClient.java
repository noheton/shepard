package de.dlr.shepard.v2.transform.urscript.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.v2.transform.urscript.config.UrScriptInterpreterConfig;
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
 * URSCRIPT-TRAJECTORY-1 — typed REST client for the URScript interpreter sidecar.
 *
 * <p>Protocol:
 * <pre>
 *   POST {baseUrl}/interpret  → 200 {trajectoryAppId, warnings, ...}
 *   GET  {baseUrl}/health     → 200 {status, version, ...}
 * </pre>
 *
 * <p>Uses {@link java.net.http.HttpClient} (no Quarkus REST Client dependency)
 * per the in-tree precedent in {@code KrlSidecarClient} — keeps the dependency
 * surface tight and stays consistent with the SPI-neutral posture.
 *
 * <h2>Error model</h2>
 * <p>The client surfaces four discriminated outcomes via {@link SidecarOutcome}:
 * <ul>
 *   <li>{@code OK} — 2xx with parsed JSON body.</li>
 *   <li>{@code SIDECAR_ERROR} — the sidecar responded with 4xx or 5xx.</li>
 *   <li>{@code UNREACHABLE} — IO / connect failure; the sidecar is likely not running.</li>
 *   <li>{@code TIMEOUT} — the call exceeded the configured deadline.</li>
 * </ul>
 */
@ApplicationScoped
public class UrScriptSidecarClient {

  @Inject UrScriptInterpreterConfig config;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private HttpClient httpClient;

  @PostConstruct
  void init() {
    // Force HTTP/1.1 — the sidecar is FastAPI + uvicorn, which doesn't
    // negotiate HTTP/2 cleanly with Java's default upgrade attempt.
    this.httpClient = HttpClient.newBuilder()
      .version(HttpClient.Version.HTTP_1_1)
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  }

  /**
   * POST to {@code /interpret}. The {@code body} is serialised to JSON via Jackson;
   * the response body is parsed back as a generic {@link Map}.
   *
   * @param body the request body
   * @return the discriminated outcome
   */
  public SidecarOutcome interpret(Map<String, Object> body) {
    URI uri;
    try {
      uri = URI.create(config.getSidecarUrl() + "/interpret");
    } catch (IllegalArgumentException ex) {
      Log.errorf("URScript: malformed sidecar URL %s", config.getSidecarUrl());
      return SidecarOutcome.unreachable("Malformed sidecar URL: " + ex.getMessage());
    }
    return sendJson(uri, "POST", body);
  }

  /**
   * GET {@code /health}. Returns the parsed JSON body on 2xx.
   */
  public SidecarOutcome health() {
    URI uri;
    try {
      uri = URI.create(config.getSidecarUrl() + "/health");
    } catch (IllegalArgumentException ex) {
      return SidecarOutcome.unreachable("Malformed sidecar URL: " + ex.getMessage());
    }
    return sendJson(uri, "GET", null);
  }

  /**
   * Visible-for-test seam — swap the underlying client for a stub in unit tests.
   */
  public void setHttpClientForTest(HttpClient client) {
    this.httpClient = client;
  }

  // ── internals ─────────────────────────────────────────────────────────────

  SidecarOutcome sendJson(URI uri, String method, Map<String, Object> body) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
      .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
      .header("Accept", "application/json")
      .header("User-Agent", "shepard-urscript-interpreter-client/1.0");

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
      Log.warnf("URScript: sidecar call timed out after %ds (url=%s)", config.getTimeoutSeconds(), uri);
      return SidecarOutcome.timeout("Sidecar call timed out after " + config.getTimeoutSeconds() + "s");
    } catch (IOException ex) {
      Log.warnf("URScript: sidecar unreachable (%s): %s", uri, ex.getMessage());
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
      Log.warnf("URScript: sidecar returned status=%d body=%s", status, snippet);
      return SidecarOutcome.sidecarError(status, snippet);
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> parsed = objectMapper.readValue(bodyBytes, Map.class);
      return SidecarOutcome.ok(parsed);
    } catch (IOException ex) {
      Log.warnf("URScript: failed to parse sidecar response: %s", ex.getMessage());
      return SidecarOutcome.sidecarError(500, "Sidecar returned non-JSON body");
    }
  }

  // ── result envelope ───────────────────────────────────────────────────────

  /**
   * Discriminated result of a sidecar call. The service layer maps
   * {@link #status} to the user-visible HTTP code.
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

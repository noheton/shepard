package de.dlr.shepard.plugins.minter.datacite.daos;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Objects;

/**
 * KIP1d — thin JDK {@link HttpClient} wrapper used by
 * {@code DataciteMinter} for DOI mint + back-fill calls.
 *
 * <p>Pulled into its own bean so unit tests can mock the HTTP
 * boundary without poking the static {@link HttpClient#newBuilder()}
 * surface or wiring a WireMock instance for every assertion.
 *
 * <p>Timeouts:
 *
 * <ul>
 *   <li>Connect: 10s — Fabrica's TLS handshake from EU compute can
 *       run several seconds on cold connect.</li>
 *   <li>Request: 30s — DataCite's API is sometimes slow to register
 *       a new DOI; the retry hook in {@code DataciteMinter} adds
 *       defence on top.</li>
 * </ul>
 *
 * <p>Returns a small POJO rather than the raw {@link HttpResponse}
 * so the test harness doesn't need to fabricate JDK response shapes.
 */
@ApplicationScoped
public class DataciteHttpClient {

  /** Connect timeout. */
  public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /** Per-request timeout — the bound on a single mint attempt. */
  public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final HttpClient client;

  /** Production constructor — builds the default JDK client. */
  public DataciteHttpClient() {
    this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  /** Visible for tests — inject a stubbed client. */
  public DataciteHttpClient(HttpClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  /**
   * POST a JSON body to {@code url}. Returns the status code +
   * response body string. Throws {@link RuntimeException} on
   * network / timeout / interrupt — the caller decides retry policy.
   */
  public DataciteHttpResponse post(String url, String body, String authHeader) {
    return send("POST", url, body, authHeader);
  }

  /** PUT — same shape as {@link #post(String, String, String)}. */
  public DataciteHttpResponse put(String url, String body, String authHeader) {
    return send("PUT", url, body, authHeader);
  }

  /**
   * Issue a GET, used by {@code POST .../test-connection}. Returns
   * status code + body string; does NOT throw on network errors so
   * the admin endpoint can report `reachable=false` with a useful
   * status code where available.
   */
  public DataciteHttpResponse getDiagnostic(String url, String authHeader) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
      .timeout(REQUEST_TIMEOUT)
      .header("Accept", "application/vnd.api+json")
      .GET();
    if (authHeader != null && !authHeader.isBlank()) {
      builder.header("Authorization", authHeader);
    }
    HttpRequest request = builder.build();
    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      return new DataciteHttpResponse(response.statusCode(), response.body());
    } catch (IOException ioe) {
      Log.debugf(ioe, "KIP1d: diagnostic GET %s failed (network)", url);
      return new DataciteHttpResponse(0, "network: " + ioe.getMessage());
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return new DataciteHttpResponse(0, "interrupted: " + ie.getMessage());
    }
  }

  DataciteHttpResponse send(String method, String url, String body, String authHeader) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
      .timeout(REQUEST_TIMEOUT)
      .header("Content-Type", "application/vnd.api+json")
      .header("Accept", "application/vnd.api+json");
    if (authHeader != null && !authHeader.isBlank()) {
      builder.header("Authorization", authHeader);
    }
    HttpRequest.BodyPublisher publisher = body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body);
    HttpRequest request = builder.method(method, publisher).build();
    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      return new DataciteHttpResponse(response.statusCode(), response.body());
    } catch (IOException ioe) {
      throw new RuntimeException("DataCite HTTP " + method + " " + url + " failed: " + ioe.getMessage(), ioe);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("DataCite HTTP " + method + " " + url + " interrupted", ie);
    }
  }

  /** Simple value carrier — {@code statusCode==0} signals a network error. */
  public record DataciteHttpResponse(int statusCode, String body) {}
}

package de.dlr.shepard.plugins.minter.epic.daos;

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
 * KIP1c — thin JDK {@link HttpClient} wrapper used by
 * {@code EpicMinter} for handle mint calls against the ePIC
 * Handle Service REST API (B2HANDLE-compatible).
 *
 * <p>Pulled into its own bean so unit tests can mock the HTTP
 * boundary without poking the static {@link HttpClient#newBuilder()}
 * surface.
 *
 * <p>Timeouts:
 *
 * <ul>
 *   <li>Connect: 10s — TLS handshake from EU compute.</li>
 *   <li>Request: 30s — handle registration can be slow under load.</li>
 * </ul>
 *
 * <p>Returns a small POJO rather than the raw {@link HttpResponse}
 * so the test harness doesn't need to fabricate JDK response shapes.
 */
@ApplicationScoped
public class EpicHttpClient {

  /** Connect timeout. */
  public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  /** Per-request timeout. */
  public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private final HttpClient client;

  /** Production constructor — builds the default JDK client. */
  public EpicHttpClient() {
    this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  /** Visible for tests — inject a stubbed client. */
  public EpicHttpClient(HttpClient client) {
    this.client = Objects.requireNonNull(client, "client");
  }

  /**
   * PUT a JSON body to {@code url} (used for handle mint and update).
   * Returns the status code + response body string. Throws
   * {@link RuntimeException} on network / timeout / interrupt — the
   * caller decides retry policy.
   */
  public EpicHttpResponse put(String url, String body, String authHeader) {
    return send("PUT", url, body, authHeader);
  }

  /**
   * Issue a GET, used by {@code POST .../test-connection}. Returns
   * status code + body string; does NOT throw on network errors so
   * the admin endpoint can report {@code reachable=false} with a
   * useful status code where available.
   */
  public EpicHttpResponse getDiagnostic(String url, String authHeader) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
      .timeout(REQUEST_TIMEOUT)
      .header("Accept", "application/json")
      .GET();
    if (authHeader != null && !authHeader.isBlank()) {
      builder.header("Authorization", authHeader);
    }
    HttpRequest request = builder.build();
    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      return new EpicHttpResponse(response.statusCode(), response.body());
    } catch (IOException ioe) {
      Log.debugf(ioe, "KIP1c: diagnostic GET %s failed (network)", url);
      return new EpicHttpResponse(0, "network: " + ioe.getMessage());
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      return new EpicHttpResponse(0, "interrupted: " + ie.getMessage());
    }
  }

  EpicHttpResponse send(String method, String url, String body, String authHeader) {
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
      .timeout(REQUEST_TIMEOUT)
      .header("Content-Type", "application/json")
      .header("Accept", "application/json");
    if (authHeader != null && !authHeader.isBlank()) {
      builder.header("Authorization", authHeader);
    }
    HttpRequest.BodyPublisher publisher = body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body);
    HttpRequest request = builder.method(method, publisher).build();
    try {
      HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
      return new EpicHttpResponse(response.statusCode(), response.body());
    } catch (IOException ioe) {
      throw new RuntimeException("ePIC HTTP " + method + " " + url + " failed: " + ioe.getMessage(), ioe);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("ePIC HTTP " + method + " " + url + " interrupted", ie);
    }
  }

  /** Simple value carrier — {@code statusCode==0} signals a network error. */
  public record EpicHttpResponse(int statusCode, String body) {}
}

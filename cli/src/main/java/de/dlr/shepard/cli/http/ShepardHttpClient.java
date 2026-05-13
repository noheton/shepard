package de.dlr.shepard.cli.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Function;

/**
 * Thin wrapper around {@link java.net.http.HttpClient} for the
 * read-only commands shipped in Phase 1. Adds:
 * <ul>
 *   <li>{@code X-API-KEY} header (per the shepard wire convention,
 *       see {@code JWTFilter.parseApiKey}).</li>
 *   <li>{@code Accept: application/json} on every call.</li>
 *   <li>Operator-readable error mapping — no stack traces by
 *       default. {@link AdminCliException} wraps connect/auth/5xx
 *       cases with a one-line human message; the caller decides
 *       whether {@code --verbose} surfaces the cause.</li>
 * </ul>
 *
 * <p>The class is intentionally small and stateless past
 * construction so it can be re-used across the Picocli command
 * graph. Tests inject a mock {@link HttpClient} to simulate
 * responses without hitting the network.
 */
public final class ShepardHttpClient {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

  private final HttpClient httpClient;
  private final String baseUrl;
  private final String apiKey;

  public ShepardHttpClient(HttpClient httpClient, String baseUrl, String apiKey) {
    this.httpClient = httpClient;
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.apiKey = apiKey;
  }

  /** Convenience: default {@link HttpClient} with a 10-second connect timeout. */
  public static HttpClient defaultClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  public static ObjectMapper mapper() {
    return MAPPER;
  }

  /**
   * Perform a {@code GET} against {@code path} and decode the body
   * as JSON of the given type. Throws {@link AdminCliException} on
   * any non-2xx response or transport failure.
   */
  public <T> T getJson(String path, TypeReference<T> type) {
    HttpResponse<String> response = get(path);
    return decode(response.body(), body -> {
      try {
        return MAPPER.readValue(body, type);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * Perform a {@code POST} against {@code path} with a JSON-serialised
   * {@code requestBody}, decode the response as JSON of the given
   * {@code responseType}. Throws {@link AdminCliException} on any
   * non-2xx response or transport failure.
   */
  public <T> T postJson(String path, Object requestBody, TypeReference<T> responseType) {
    HttpResponse<String> response = post(path, requestBody);
    return decode(response.body(), body -> {
      try {
        return MAPPER.readValue(body, responseType);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  /** Raw {@code POST} variant — serialises {@code requestBody} via Jackson. */
  public HttpResponse<String> post(String path, Object requestBody) {
    final String json;
    try {
      json = requestBody == null ? "{}" : MAPPER.writeValueAsString(requestBody);
    } catch (IOException e) {
      throw new AdminCliException("Could not serialise request body to JSON: " + e.getMessage(), e);
    }
    URI uri = URI.create(baseUrl + ensureLeadingSlash(path));
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
      .POST(HttpRequest.BodyPublishers.ofString(json))
      .timeout(Duration.ofSeconds(120))
      .header("Accept", "application/json")
      .header("Content-Type", "application/json");
    if (apiKey != null && !apiKey.isBlank()) {
      builder.header("X-API-KEY", apiKey);
    }
    return send(builder.build(), path, false);
  }

  /** Raw {@code GET} variant — useful for endpoints that may return non-JSON (e.g. plain text). */
  public HttpResponse<String> get(String path) {
    return get(path, false);
  }

  /**
   * Raw {@code GET} variant with an opt-in to treat HTTP 503 as a
   * non-error response (returning the response unchanged). Used by
   * the health command, which expects the SmallRye-Health envelope
   * to come back on both 200 (UP) and 503 (DOWN).
   */
  public HttpResponse<String> get(String path, boolean allow503) {
    URI uri = URI.create(baseUrl + ensureLeadingSlash(path));
    HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
      .GET()
      .timeout(Duration.ofSeconds(30))
      .header("Accept", "application/json");
    if (apiKey != null && !apiKey.isBlank()) {
      builder.header("X-API-KEY", apiKey);
    }
    return send(builder.build(), path, allow503);
  }

  /**
   * Dispatch a fully-built request and map status codes / transport
   * failures to {@link AdminCliException}. Shared by the GET, POST,
   * and any future PATCH/PUT/DELETE paths so the operator-readable
   * error messages stay consistent.
   */
  HttpResponse<String> send(HttpRequest request, String path, boolean allow503) {
    HttpResponse<String> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (java.net.ConnectException e) {
      throw new AdminCliException(
        "Cannot connect to shepard at " + baseUrl + " — is the backend reachable?",
        e
      );
    } catch (IOException e) {
      throw new AdminCliException("Network error talking to " + baseUrl + ": " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AdminCliException("Request to " + baseUrl + " was interrupted.", e);
    }

    int status = response.statusCode();
    if (status >= 200 && status < 300) {
      return response;
    }
    if (allow503 && status == 503) {
      return response;
    }
    String prefix = baseUrl + path;
    if (status == 401) {
      throw new AdminCliException(
        "401 Unauthorized from " + prefix + " — set SHEPARD_ADMIN_API_KEY or pass --api-key."
      );
    }
    if (status == 403) {
      throw new AdminCliException(
        "403 Forbidden from " + prefix + " — the API key lacks the instance-admin role."
      );
    }
    if (status == 404) {
      throw new AdminCliException(
        "404 Not Found from " + prefix + " — endpoint missing (backend may be older than this CLI expects)."
      );
    }
    if (status >= 500) {
      throw new AdminCliException("Backend error " + status + " from " + prefix + ": " + summarise(response.body()));
    }
    throw new AdminCliException("Unexpected HTTP " + status + " from " + prefix + ": " + summarise(response.body()));
  }

  private static <T> T decode(String body, Function<String, T> fn) {
    try {
      return fn.apply(body);
    } catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof JsonMappingException jme) {
        throw new AdminCliException("Could not parse response body as JSON: " + jme.getOriginalMessage(), jme);
      }
      throw new AdminCliException("Could not parse response body as JSON: " + e.getMessage(), e);
    }
  }

  private static String stripTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  private static String ensureLeadingSlash(String s) {
    return s.startsWith("/") ? s : "/" + s;
  }

  private static String summarise(String body) {
    if (body == null || body.isBlank()) return "(empty body)";
    String trimmed = body.trim();
    return trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed;
  }
}

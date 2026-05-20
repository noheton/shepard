package de.dlr.shepard.plugins.aas.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.plugins.aas.v2.io.AasShellDescriptorIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * HTTP client for the IDTA AAS Registry REST API (AAS1-reg).
 *
 * <p>Stateless; all methods are safe to call from any thread.
 * The underlying {@link HttpClient} is long-lived (one per JVM).
 * Failures are signalled via {@link RegistrationResult#success}
 * rather than exceptions — callers update the outbox accordingly.
 */
@ApplicationScoped
public class AasRegistryClient {

  private static final HttpClient HTTP = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** Result of a single shell registration attempt. */
  public record RegistrationResult(boolean success, String error) {
    public static RegistrationResult ok() {
      return new RegistrationResult(true, null);
    }

    public static RegistrationResult fail(String error) {
      return new RegistrationResult(false, error);
    }
  }

  /**
   * Register or update a shell descriptor at the given registry.
   *
   * <p>Strategy: {@code POST {registryUrl}/shell-descriptors}. A 200 or
   * 201 response is treated as success; anything else is a failure whose
   * HTTP status and truncated body are captured in the result.
   *
   * @param registryUrl base URL of the IDTA AAS Registry (no trailing slash)
   * @param apiKey      optional Bearer token (skipped when absent/blank)
   * @param descriptor  the shell descriptor to register
   */
  public RegistrationResult register(
    String registryUrl,
    Optional<String> apiKey,
    AasShellDescriptorIO descriptor
  ) {
    try {
      String body = MAPPER.writeValueAsString(descriptor);
      String url = registryUrl.stripTrailing() + "/shell-descriptors";
      var builder = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body));
      apiKey.filter(k -> !k.isBlank()).ifPresent(k -> builder.header("Authorization", "Bearer " + k));
      var response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      int status = response.statusCode();
      if (status == 200 || status == 201) {
        return RegistrationResult.ok();
      }
      String truncated = response.body();
      if (truncated != null && truncated.length() > 500) {
        truncated = truncated.substring(0, 500) + "…";
      }
      return RegistrationResult.fail("HTTP " + status + ": " + truncated);
    } catch (Exception e) {
      Log.warnf(e, "AAS1-reg: exception registering shell at %s", registryUrl);
      return RegistrationResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }
}

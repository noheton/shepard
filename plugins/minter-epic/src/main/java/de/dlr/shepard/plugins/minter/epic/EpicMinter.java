package de.dlr.shepard.plugins.minter.epic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient;
import de.dlr.shepard.plugins.minter.epic.daos.EpicHttpClient.EpicHttpResponse;
import de.dlr.shepard.plugins.minter.epic.entities.EpicMinterConfig;
import de.dlr.shepard.plugins.minter.epic.services.EpicMinterConfigService;
import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import de.dlr.shepard.publish.minter.Minter;
import de.dlr.shepard.publish.minter.MinterException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * KIP1c — ePIC handle service minter.
 *
 * <p>Implements the in-core {@link Minter} SPI. Activated by the
 * deploy-time {@code shepard.publish.minter=epic} key (which
 * {@link de.dlr.shepard.publish.minter.MinterRegistry} resolves at
 * startup). Reads runtime configuration ({@code apiBaseUrl},
 * {@code handlePrefix}, {@code credentialKey}) from the
 * {@code :EpicMinterConfig} singleton owned by
 * {@link EpicMinterConfigService}.
 *
 * <p>Mint flow per {@code aidocs/16} KIP1c:
 *
 * <ol>
 *   <li>Load config; throw {@link MinterException} when disabled or
 *       missing required fields.</li>
 *   <li>Generate a UUID v4 suffix for the handle.</li>
 *   <li>Build the ePIC JSON body (B2HANDLE format:
 *       {@code [{"type":"URL","data":"<locatorUrl>"}]}) plus optional
 *       metadata values (name, version).</li>
 *   <li>PUT to {@code <apiBaseUrl>/handles/<prefix>/<suffix>} with
 *       HTTP Basic auth (credential decrypted from
 *       {@code :EpicMinterConfig.credentialKey}).</li>
 *   <li>On 200/201: return a {@link MintResult} with
 *       {@code pid="https://hdl.handle.net/<prefix>/<suffix>"},
 *       {@code minterId="epic"}, {@code mintedAt=now}.</li>
 *   <li>On 5xx / network: retry once with 1s backoff; on second
 *       failure, throw {@link MinterException}.</li>
 *   <li>On 4xx: surface a clean operator-readable message in the
 *       {@link MinterException}.</li>
 * </ol>
 */
@ApplicationScoped
public class EpicMinter implements Minter {

  /** Stable adapter id — must match {@code shepard.publish.minter=}. */
  public static final String ID = "epic";

  /** hdl.handle.net resolver base URL — global standard resolver. */
  static final String HDL_RESOLVER_BASE = "https://hdl.handle.net/";

  @Inject
  EpicMinterConfigService configService;

  @Inject
  EpicHttpClient http;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isEnabled() {
    try {
      EpicMinterConfig cfg = configService.current();
      return cfg.isEnabled() &&
        notBlank(cfg.getApiBaseUrl()) &&
        notBlank(cfg.getHandlePrefix()) &&
        notBlank(cfg.getCredentialKey());
    } catch (RuntimeException e) {
      Log.warnf(e, "KIP1c: isEnabled() check failed");
      return false;
    }
  }

  @Override
  public MintResult mint(MintRequest req) {
    EpicMinterConfig cfg = configService.current();
    if (!cfg.isEnabled()) {
      throw new MinterException(
        "ePIC minter disabled — flip via PATCH /v2/admin/minters/epic/config or `shepard-admin minters epic enable`"
      );
    }
    requireConfigField(cfg.getApiBaseUrl(), "apiBaseUrl");
    requireConfigField(cfg.getHandlePrefix(), "handlePrefix");
    requireConfigField(cfg.getCredentialKey(), "credential");

    String suffix = UUID.randomUUID().toString();
    String handlePath = cfg.getHandlePrefix() + "/" + suffix;
    String url = stripTrailingSlash(cfg.getApiBaseUrl()) + "/handles/" + handlePath;

    String body = buildRequestBody(req);
    String authHeader = basicAuth(configService.resolvePlaintext());

    EpicHttpResponse response = putWithRetry(url, body, authHeader);

    if (response.statusCode() != 200 && response.statusCode() != 201) {
      throw new MinterException(
        "ePIC mint failed: HTTP " +
        response.statusCode() +
        " — " +
        truncate(response.body(), 500)
      );
    }

    String pid = HDL_RESOLVER_BASE + handlePath;
    Log.infof(
      "KIP1c: minted ePIC handle %s for entityKind=%s appId=%s versionNumber=%d",
      pid,
      req.entityKind(),
      req.appId(),
      req.versionNumber()
    );

    return new MintResult(pid, Instant.now(), ID);
  }

  // ─── helpers ────────────────────────────────────────────────────

  /**
   * Build the B2HANDLE-compatible JSON body for a PUT mint call.
   *
   * <p>Format: an array of typed handle value records. The required
   * {@code URL} record points to the entity's landing page; optional
   * metadata (name, version) follow as additional value records.
   */
  String buildRequestBody(MintRequest req) {
    try {
      ArrayNode root = objectMapper.createArrayNode();

      ObjectNode urlRecord = root.addObject();
      urlRecord.put("type", "URL");
      urlRecord.put("parsed_data", req.locatorUrl());

      if (notBlank(req.metadata().get("name"))) {
        ObjectNode nameRecord = root.addObject();
        nameRecord.put("type", "NAME");
        nameRecord.put("parsed_data", req.metadata().get("name"));
      }

      int versionNumber = Math.max(req.versionNumber(), 1);
      ObjectNode versionRecord = root.addObject();
      versionRecord.put("type", "VERSION");
      versionRecord.put("parsed_data", "v" + versionNumber);

      return objectMapper.writeValueAsString(root);
    } catch (IOException e) {
      throw new MinterException("Failed to serialise ePIC request body", e);
    }
  }

  /**
   * PUT with at-most-one retry. Retried on network exception or 5xx;
   * 4xx is final.
   */
  EpicHttpResponse putWithRetry(String url, String body, String authHeader) {
    EpicHttpResponse first;
    try {
      first = http.put(url, body, authHeader);
    } catch (RuntimeException firstFailure) {
      sleepBackoff();
      try {
        return http.put(url, body, authHeader);
      } catch (RuntimeException retryFailure) {
        throw new MinterException(
          "ePIC mint failed after retry: " + retryFailure.getMessage(),
          retryFailure
        );
      }
    }
    if (first.statusCode() >= 500) {
      sleepBackoff();
      try {
        return http.put(url, body, authHeader);
      } catch (RuntimeException retryFailure) {
        throw new MinterException(
          "ePIC mint failed after 5xx retry: " + retryFailure.getMessage(),
          retryFailure
        );
      }
    }
    return first;
  }

  static String basicAuth(String credential) {
    String raw = (credential == null) ? "" : credential;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  static String stripTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  static String truncate(String s, int max) {
    if (s == null) return "";
    return s.length() <= max ? s : s.substring(0, max) + "…";
  }

  static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  static void requireConfigField(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new MinterException(
        "ePIC minter '" +
        name +
        "' is not configured — set via `shepard-admin minters epic set-" +
        kebab(name) +
        "` or PATCH /v2/admin/minters/epic/config"
      );
    }
  }

  static String kebab(String camel) {
    return camel.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
  }

  private static void sleepBackoff() {
    try {
      Thread.sleep(1000L);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }
  }
}

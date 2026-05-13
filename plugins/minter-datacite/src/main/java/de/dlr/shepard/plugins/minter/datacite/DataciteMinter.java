package de.dlr.shepard.plugins.minter.datacite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient;
import de.dlr.shepard.plugins.minter.datacite.daos.DataciteHttpClient.DataciteHttpResponse;
import de.dlr.shepard.plugins.minter.datacite.entities.DataciteMinterConfig;
import de.dlr.shepard.plugins.minter.datacite.services.DataciteMinterConfigService;
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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;

/**
 * KIP1d — DataCite Fabrica DOI minter.
 *
 * <p>Implements the in-core {@link Minter} SPI. Activated by the
 * deploy-time {@code shepard.publish.minter=datacite} key (which
 * {@link de.dlr.shepard.publish.minter.MinterRegistry} resolves at
 * startup). Reads runtime configuration ({@code apiBaseUrl},
 * {@code handlePrefix}, {@code repositoryId}, {@code passwordCipher},
 * {@code publisher}, {@code landingPageBase}, {@code defaultState})
 * from the {@code :DataciteMinterConfig} singleton owned by
 * {@link DataciteMinterConfigService}.
 *
 * <p>Mint flow per {@code aidocs/16} KIP1d:
 *
 * <ol>
 *   <li>Load config; throw {@link MinterException} when disabled or
 *       missing required fields.</li>
 *   <li>Build the DataCite JSON:API request body
 *       ({@code data.attributes.{prefix,creators,titles,publisher,
 *       publicationYear,types,url,version,event,relatedIdentifiers}}).</li>
 *   <li>POST to {@code <apiBaseUrl>/dois} with HTTP Basic auth
 *       ({@code repositoryId} + decrypted password).</li>
 *   <li>On 201 Created: extract the DOI from {@code data.id}, return
 *       a {@link MintResult} ({@code id="datacite"},
 *       {@code mintedAt=now}).</li>
 *   <li>On 5xx / network: retry once with 1s backoff; on second
 *       failure, throw {@link MinterException}.</li>
 *   <li>On 4xx: surface a clean operator-readable message in the
 *       {@link MinterException}; the in-core
 *       {@code PublishRest.toProblem} maps it to RFC 7807
 *       {@code publish.minter.failed}.</li>
 * </ol>
 *
 * <p><b>Versioning.</b> KIP1d respects {@link MintRequest#versionNumber()}
 * (the first-class field surfaced by KIP1h's {@code PublishService} —
 * computed as {@code findLatestVersionNumber + 1} over the entity's
 * existing {@code :Publication} rows). When {@code n > 1}, a
 * {@code relatedIdentifiers} entry with
 * {@code IsNewVersionOf=<previous-pid>} is added; the previous
 * publication is then back-filled with the inverse {@code HasVersion}
 * relation via a {@code PUT /dois/<previous>}. The previous-PID
 * lookup is delegated to a {@link PreviousPublicationResolver}
 * function so this class stays decoupled from the in-core
 * {@code PublicationDAO} for unit-test ergonomics.
 *
 * <p>Phase 1 mints in the configured {@code defaultState} (default
 * {@code draft} — operator promotes via DataCite Fabrica UI).
 */
@ApplicationScoped
public class DataciteMinter implements Minter {

  /** Stable adapter id — must match {@code shepard.publish.minter=}. */
  public static final String ID = "datacite";

  /** DataCite version metadata field uses literal "v<n>" formatting. */
  static final String VERSION_PREFIX = "v";

  @Inject
  DataciteMinterConfigService configService;

  @Inject
  DataciteHttpClient http;

  /**
   * Pluggable hook for "what's the previous version's PID for this
   * appId?" — used to build {@code IsNewVersionOf}. Defaults to a
   * no-op resolver; production wires it to the in-core
   * {@code PublicationDAO} via {@link #previousResolver(PreviousPublicationResolver)}.
   * (Set up imperatively rather than CDI-injected to avoid a
   * cross-plugin compile-time dependency from KIP1d on the in-core
   * publish package's DAO bean lifecycle.)
   */
  private volatile PreviousPublicationResolver previousResolver = (appId, n) -> null;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isEnabled() {
    try {
      DataciteMinterConfig cfg = configService.current();
      return cfg.isEnabled() &&
        notBlank(cfg.getHandlePrefix()) &&
        notBlank(cfg.getRepositoryId()) &&
        notBlank(cfg.getPasswordCipher()) &&
        notBlank(cfg.getPublisher()) &&
        notBlank(cfg.getLandingPageBase());
    } catch (RuntimeException e) {
      Log.warnf(e, "KIP1d: isEnabled() check failed");
      return false;
    }
  }

  @Override
  public MintResult mint(MintRequest req) {
    DataciteMinterConfig cfg = configService.current();
    if (!cfg.isEnabled()) {
      throw new MinterException("DataCite minter disabled — flip via PATCH /v2/admin/minters/datacite/config or `shepard-admin minters datacite enable`");
    }
    requireConfigField(cfg.getHandlePrefix(), "handlePrefix");
    requireConfigField(cfg.getRepositoryId(), "repositoryId");
    requireConfigField(cfg.getPasswordCipher(), "credential");
    requireConfigField(cfg.getPublisher(), "publisher");
    requireConfigField(cfg.getLandingPageBase(), "landingPageBase");

    int versionNumber = extractVersionNumber(req);
    String previousPid = versionNumber > 1
      ? previousResolver.previousPid(req.appId(), versionNumber)
      : null;

    String body = buildRequestBody(cfg, req, versionNumber, previousPid);
    String authHeader = basicAuth(cfg.getRepositoryId(), configService.resolvePlaintext());

    DataciteHttpResponse response = postWithRetry(cfg.getApiBaseUrl() + "/dois", body, authHeader);

    if (response.statusCode() != 201) {
      throw new MinterException(
        "DataCite mint failed: HTTP " +
        response.statusCode() +
        " — " +
        truncate(response.body(), 500)
      );
    }
    String doi = extractDoi(response.body());
    Log.infof(
      "KIP1d: minted DOI %s for entityKind=%s appId=%s versionNumber=%d",
      doi,
      req.entityKind(),
      req.appId(),
      versionNumber
    );

    // Back-fill HasVersion on the previous publication so the chain
    // resolves both ways in DataCite Commons. Failure is logged
    // but not fatal — the new mint already succeeded.
    if (previousPid != null) {
      try {
        backfillHasVersion(cfg, previousPid, doi, authHeader);
      } catch (RuntimeException e) {
        Log.warnf(
          e,
          "KIP1d: failed to back-fill HasVersion on previous DOI %s — chain may be one-directional",
          previousPid
        );
      }
    }

    return new MintResult(doi, Instant.now(), ID);
  }

  /**
   * Set the resolver used to look up the previous version's PID for
   * a given appId. Wired imperatively at backend startup (the
   * plugin's {@code DataciteMinterPluginManifest.onRegister} hook
   * connects this to the in-core {@code PublicationDAO}); tests pass
   * stub resolvers directly.
   */
  public void previousResolver(PreviousPublicationResolver resolver) {
    this.previousResolver = resolver == null ? (a, n) -> null : resolver;
  }

  /** Visible for tests + the plugin manifest's wire-up. */
  public PreviousPublicationResolver currentPreviousResolver() {
    return previousResolver;
  }

  // ─── helpers ────────────────────────────────────────────────────

  String buildRequestBody(
    DataciteMinterConfig cfg,
    MintRequest req,
    int versionNumber,
    String previousPid
  ) {
    ObjectNode root = objectMapper.createObjectNode();
    ObjectNode data = root.putObject("data");
    data.put("type", "dois");
    ObjectNode attrs = data.putObject("attributes");
    attrs.put("prefix", cfg.getHandlePrefix());

    ArrayNode creators = attrs.putArray("creators");
    ObjectNode creator = creators.addObject();
    String creatorName = firstNonBlank(req.metadata().get("rightsHolder"), cfg.getPublisher());
    creator.put("name", creatorName);
    creator.put("nameType", "Personal");

    ArrayNode titles = attrs.putArray("titles");
    ObjectNode title = titles.addObject();
    String resolvedTitle = firstNonBlank(req.metadata().get("name"), req.entityKind() + " " + req.appId());
    title.put("title", resolvedTitle);

    attrs.put("publisher", cfg.getPublisher());
    attrs.put(
      "publicationYear",
      ZonedDateTime.now(ZoneOffset.UTC).getYear()
    );

    ObjectNode types = attrs.putObject("types");
    String digitalObjectType = firstNonBlank(req.metadata().get("digitalObjectType"), "Dataset");
    types.put("resourceTypeGeneral", mapToResourceTypeGeneral(digitalObjectType));

    String landingUrl = stripTrailingSlash(cfg.getLandingPageBase()) + "/" + req.entityKind() + "/" + req.appId();
    attrs.put("url", landingUrl);

    attrs.put("version", VERSION_PREFIX + versionNumber);

    attrs.put("event", eventFor(cfg.getDefaultState()));

    if (previousPid != null) {
      ArrayNode related = attrs.putArray("relatedIdentifiers");
      ObjectNode rel = related.addObject();
      rel.put("relatedIdentifier", previousPid);
      rel.put("relatedIdentifierType", "DOI");
      rel.put("relationType", "IsNewVersionOf");
    }

    try {
      return objectMapper.writeValueAsString(root);
    } catch (IOException e) {
      throw new MinterException("Failed to serialise DataCite request body", e);
    }
  }

  /**
   * PUT a HasVersion relation onto the previous publication's DOI so
   * DataCite's Commons can render the version chain bidirectionally.
   */
  void backfillHasVersion(
    DataciteMinterConfig cfg,
    String previousPid,
    String newPid,
    String authHeader
  ) {
    ObjectNode root = objectMapper.createObjectNode();
    ObjectNode data = root.putObject("data");
    data.put("type", "dois");
    data.put("id", previousPid);
    ObjectNode attrs = data.putObject("attributes");
    ArrayNode related = attrs.putArray("relatedIdentifiers");
    ObjectNode rel = related.addObject();
    rel.put("relatedIdentifier", newPid);
    rel.put("relatedIdentifierType", "DOI");
    rel.put("relationType", "HasVersion");
    String body;
    try {
      body = objectMapper.writeValueAsString(root);
    } catch (IOException e) {
      throw new MinterException("Failed to serialise back-fill body", e);
    }
    String url = cfg.getApiBaseUrl() + "/dois/" + previousPid;
    DataciteHttpResponse response = http.put(url, body, authHeader);
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new MinterException(
        "Back-fill HasVersion to " +
        previousPid +
        " failed: HTTP " +
        response.statusCode() +
        " — " +
        truncate(response.body(), 200)
      );
    }
  }

  /**
   * POST with at-most-one retry. Retried on network exception or 5xx;
   * 4xx is final.
   */
  DataciteHttpResponse postWithRetry(String url, String body, String authHeader) {
    DataciteHttpResponse first;
    try {
      first = http.post(url, body, authHeader);
    } catch (RuntimeException firstFailure) {
      sleepBackoff();
      try {
        return http.post(url, body, authHeader);
      } catch (RuntimeException retryFailure) {
        throw new MinterException(
          "DataCite mint failed after retry: " + retryFailure.getMessage(),
          retryFailure
        );
      }
    }
    if (first.statusCode() >= 500) {
      sleepBackoff();
      try {
        return http.post(url, body, authHeader);
      } catch (RuntimeException retryFailure) {
        throw new MinterException(
          "DataCite mint failed after 5xx retry: " + retryFailure.getMessage(),
          retryFailure
        );
      }
    }
    return first;
  }

  /**
   * Resolve the effective version number for the mint call. KIP1h
   * makes {@code versionNumber} a first-class field on
   * {@link MintRequest}; this helper retains backwards-tolerance
   * for any caller / test that ever constructed a {@code MintRequest}
   * with {@code versionNumber <= 0} (the in-core
   * {@code MintRequest} constructor now rejects those, but we
   * defensively clamp to 1 just in case).
   */
  static int extractVersionNumber(MintRequest req) {
    int n = req.versionNumber();
    return Math.max(n, 1);
  }

  String extractDoi(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      JsonNode id = root.path("data").path("id");
      if (id.isMissingNode() || id.isNull() || id.asText().isBlank()) {
        throw new MinterException("DataCite response did not carry data.id: " + truncate(responseBody, 300));
      }
      return id.asText();
    } catch (IOException e) {
      throw new MinterException("Could not parse DataCite response body: " + e.getMessage(), e);
    }
  }

  static String eventFor(String state) {
    if (state == null) return "draft";
    return switch (state.toLowerCase()) {
      case DataciteMinterConfig.STATE_REGISTERED -> "register";
      case DataciteMinterConfig.STATE_FINDABLE -> "publish";
      default -> "draft";
    };
  }

  /**
   * Map shepard's KIP "digitalObjectType" to DataCite's
   * resourceTypeGeneral vocabulary. Conservative fallback to
   * "Dataset" for everything we don't recognise — the most common
   * shepard payload is research data, and "Dataset" is DataCite's
   * default semantics for "thing with rows".
   */
  static String mapToResourceTypeGeneral(String digitalObjectType) {
    if (digitalObjectType == null) return "Dataset";
    String lower = digitalObjectType.toLowerCase();
    return switch (lower) {
      case "collection", "collections" -> "Collection";
      case "software" -> "Software";
      case "publication", "text" -> "Text";
      case "image" -> "Image";
      default -> "Dataset";
    };
  }

  static String basicAuth(String user, String password) {
    String raw = user + ":" + (password == null ? "" : password);
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  static String stripTrailingSlash(String s) {
    if (s == null) return "";
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  static String firstNonBlank(String a, String fallback) {
    if (a != null && !a.isBlank()) return a;
    return fallback;
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
        "DataCite minter '" +
        name +
        "' is not configured — set via `shepard-admin minters datacite set-" +
        kebab(name) +
        "` or PATCH /v2/admin/minters/datacite/config"
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

  // ─── Inner types ────────────────────────────────────────────────

  /**
   * Resolves the previous version's PID for a given entity. Returns
   * {@code null} when no previous version exists (or when the lookup
   * is unwired in tests).
   */
  @FunctionalInterface
  public interface PreviousPublicationResolver {
    String previousPid(String entityAppId, int currentVersionNumber);
  }
}

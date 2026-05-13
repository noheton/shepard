package de.dlr.shepard.publish.minter;

import java.util.Collections;
import java.util.Map;

/**
 * Input payload for {@link Minter#mint(MintRequest)} — describes the
 * shepard entity that's about to receive a PID.
 *
 * <p>KIP1a baseline shape per {@code aidocs/66 §3-§5}. KIP1h grew the
 * {@code versionNumber} field for Phase-1 versioned PIDs: the core
 * {@code PublishService} computes {@code findLatestVersionNumber + 1}
 * over the entity's existing {@code :Publication} rows and stamps the
 * value onto the request, so plugin authors don't have to re-query
 * the graph. Adapters that don't care about version segments (e.g.
 * ePIC, which mints opaque handles) can ignore the field.
 *
 * <p>Fields kept minimal-by-design: the registry pulls richer fields
 * (creator, licence, keywords, dateCreated/dateModified) from the
 * entity itself when the REST layer builds the request, but the
 * {@link Minter} interface only needs the wire-shape identifiers +
 * the locator URL + the version so an adapter (local, ePIC,
 * DataCite — KIP1c/d) can issue the call without re-loading the
 * entity.
 *
 * @param entityKind    shepard's entity-kind name — one of the
 *                      {@link de.dlr.shepard.publish.PublishableKind} ids
 *                      ({@code "data-objects"}, {@code "collections"} in
 *                      KIP1a; future slices add bundles / files /
 *                      lab-journal-entries per {@code aidocs/66 §4.1}).
 * @param appId         the entity's stable application identifier (UUID v7);
 *                      becomes the visible portion of the PID for the local
 *                      minter, and the minter-specific suffix for ePIC /
 *                      DataCite.
 * @param locatorUrl    the fully-qualified URL the PID resolver should
 *                      point at — computed via the {@code aidocs/22}
 *                      pattern as {@code <shepard.url>/v2/{kind}/{appId}}.
 *                      Used by the KIP record's {@code digitalObjectLocator}.
 * @param versionNumber 1-based version number ({@code 1} on first
 *                      publish, {@code 2} after the first
 *                      {@code force=true} bump, …). KIP1h's
 *                      {@code LocalMinter} encodes this as a
 *                      trailing {@code v<n>} segment; ePIC + DataCite
 *                      (KIP1c / KIP1d) ignore it (their PIDs are
 *                      opaque handles / DOIs) and use it only for
 *                      the record's {@code isNewVersionOf} edge.
 * @param metadata      bag of additional KIP-flavoured fields the
 *                      minter may stamp on the PID record (KIP1a uses
 *                      {@code digitalObjectType}, {@code dateCreated},
 *                      {@code dateModified}, {@code rightsHolder},
 *                      {@code license}). Immutable view — adapters
 *                      should not mutate.
 */
public record MintRequest(
  String entityKind,
  String appId,
  String locatorUrl,
  int versionNumber,
  Map<String, String> metadata
) {
  public MintRequest {
    if (entityKind == null || entityKind.isBlank()) {
      throw new IllegalArgumentException("entityKind must not be null/blank");
    }
    if (appId == null || appId.isBlank()) {
      throw new IllegalArgumentException("appId must not be null/blank");
    }
    if (locatorUrl == null || locatorUrl.isBlank()) {
      throw new IllegalArgumentException("locatorUrl must not be null/blank");
    }
    if (versionNumber < 1) {
      throw new IllegalArgumentException("versionNumber must be >= 1 (got " + versionNumber + ")");
    }
    metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(Map.copyOf(metadata));
  }

  /**
   * Backwards-compatible factory for pre-KIP1h callers that don't
   * yet supply a {@code versionNumber}. Defaults to {@code 1} — the
   * Phase-1 versioning semantic that "if you didn't specify, this is
   * the first publish".
   *
   * <p>The in-tree {@code PublishService} always supplies the
   * version explicitly; this overload exists for plugin authors who
   * want to construct a request for testing without the noise.
   */
  public MintRequest(String entityKind, String appId, String locatorUrl, Map<String, String> metadata) {
    this(entityKind, appId, locatorUrl, 1, metadata);
  }
}

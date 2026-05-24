package de.dlr.shepard.provenance.filters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of {@code (targetKind, targetAppId)} from a REST
 * request path. Used by {@link ProvenanceCaptureFilter} to stamp the captured
 * {@link de.dlr.shepard.provenance.entities.Activity} with its target entity
 * so the per-entity provenance drill-down
 * ({@code GET /v2/provenance/entity/{appId}}) can find rows.
 *
 * <p><b>Two structural fixes shipped 2026-05-24 closing RDM-2026-05-24-004</b>
 * (see {@code aidocs/agent-findings/rdm-004-provenance-empty-fix-2026-05-24.md}):
 * <ul>
 *   <li><b>PROV-RESOLVER-PATHWALK</b> — right-to-left walk via {@link
 *       PathTargetParser#parse}. Subresource creates land on the leaf, not
 *       the parent. {@code POST /v2/collections/&lt;C&gt;/data-objects/&lt;D&gt;}
 *       attributes the DataObject, not the Collection.</li>
 *   <li><b>PROV-V1-NUMERIC-LOOKUP</b> — numeric ids from the v1
 *       {@code /shepard/api/...} surface resolve to {@code appId} via {@link
 *       EntityAppIdLookup}. Without this, ~280 K historic LUMEN-seed rows on
 *       production stayed {@code targetKind=NULL}.</li>
 * </ul>
 *
 * <p>For back-compat with code that still calls the old static {@code
 * resolve(path)} / {@code plural(seg)} signatures, those methods remain but
 * are marked deprecated and route through {@link PathTargetParser}. Numeric-id
 * resolution is only available via the CDI-injected instance form (the static
 * path has no DAO access).
 */
@ApplicationScoped
public class TargetEntityResolver {

  @Inject
  EntityAppIdLookup lookup;

  /**
   * Instance form — walks the path right-to-left for a {@code (kind, id)}
   * pair; resolves numeric ids to appIds via {@link EntityAppIdLookup}.
   *
   * <p>Returns empty when the path carries no known {@code (kind, id)} pair
   * or when a numeric lookup misses. The capture filter handles a missing
   * target by leaving {@code targetKind=null, targetAppId=null} on the
   * Activity row.
   */
  public Optional<TargetRef> resolve(String path) {
    return PathTargetParser.parse(path).flatMap(this::resolveRaw);
  }

  /**
   * Lift a raw parsed target to a {@link TargetRef}. Numeric ids are
   * exchanged for the entity's {@code appId} via the lookup helper; UUID
   * ids pass through unchanged.
   */
  Optional<TargetRef> resolveRaw(PathTargetParser.RawTarget raw) {
    if (!raw.isNumeric()) {
      return Optional.of(new TargetRef(raw.kind(), raw.idString()));
    }
    long numeric;
    try {
      numeric = Long.parseLong(raw.idString());
    } catch (NumberFormatException nfe) {
      return Optional.empty();
    }
    return lookup.findAppIdByNumericId(raw.kind(), numeric).map(appId -> new TargetRef(raw.kind(), appId));
  }

  // ----------------------- Deprecated static surface -----------------------
  //
  // Kept for callers that still wired against the original last-segment-only
  // resolver (notably the legacy unit test set). The new path-walk logic
  // lives in PathTargetParser; numeric ids return empty here because the
  // static surface has no DAO access.

  /**
   * @deprecated Inject {@link TargetEntityResolver} and call the instance
   *     {@link #resolve(String)} — that form resolves numeric v1 ids to
   *     appIds via {@link EntityAppIdLookup}. This static form remains only
   *     for back-compat with tests; it returns empty for numeric ids.
   */
  @Deprecated
  public static Optional<TargetRef> resolve(String path) {
    return PathTargetParser.parse(path).flatMap(raw -> {
      if (raw.isNumeric()) return Optional.empty();
      return Optional.of(new TargetRef(raw.kind(), raw.idString()));
    });
  }

  /**
   * @deprecated Look up via {@link PathTargetParser#PLURAL_TO_KIND} directly.
   *     Retained so existing tests that exercise the plural map keep working.
   *     The old fallback to title-casing for unknown plurals is gone — unknown
   *     plurals now return {@code null} so paths with verb-shaped tails (like
   *     {@code /payload}, {@code /diff}, {@code /detect-anomalies}) cannot
   *     pollute {@code targetKind}.
   */
  @Deprecated
  static String plural(String seg) {
    if (seg == null || seg.isBlank()) return null;
    return PathTargetParser.PLURAL_TO_KIND.get(seg.toLowerCase());
  }

  /** @deprecated kept for back-compat with the original test set. */
  @Deprecated
  static final Pattern UUID_RE = PathTargetParser.UUID_RE;

  /** A best-effort target-entity pointer derived from the request path. */
  public record TargetRef(String kind, String appId) {}
}

package de.dlr.shepard.provenance.filters;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pure path-walk parser that extracts the deepest {@code (kind, id)} pair from
 * a REST request path. No CDI, no DAO calls — just string parsing — so the
 * unit tests stay framework-free.
 *
 * <p>Used by {@link TargetEntityResolver} to identify which kind / id pair the
 * provenance capture filter should attribute an {@link
 * de.dlr.shepard.provenance.entities.Activity} row to.
 *
 * <p><b>Walk rule</b> — right to left. At each segment, ask:
 * <ol>
 *   <li>does this segment parse as a UUID or a pure-numeric id?</li>
 *   <li>does its left-neighbour map to a known kind via {@link #pluralToKind}?</li>
 * </ol>
 * If both yes, that's the target. If neither pair is found anywhere in the
 * path, return empty.
 *
 * <p>Two structural improvements over the previous last-segment-only resolver
 * (RDM-2026-05-24-004 bucket B + C):
 * <ul>
 *   <li>Subresource creates land on the leaf, not the parent. {@code POST
 *       /v2/collections/<COLL>/data-objects/<DO>} attributes the DataObject,
 *       not the Collection.</li>
 *   <li>Numeric ids from the v1 {@code /shepard/api/...} surface are
 *       recognised. The numeric value is returned in {@link RawTarget#idString};
 *       the caller resolves it to an appId via DAO lookup.</li>
 * </ul>
 *
 * <p><b>No title-case fallback.</b> If the kind segment is not in {@link
 * #pluralToKind} the pair is rejected — sub-segments like {@code payload},
 * {@code export}, {@code diff}, {@code detect-anomalies} must not pollute
 * the captured {@code targetKind}.
 */
public final class PathTargetParser {

  private PathTargetParser() {}

  /** Strict RFC 4122 hyphen-canonical UUID. */
  static final Pattern UUID_RE = Pattern.compile(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
  );

  /** Pure-numeric id (the v1 {@code /shepard/api/...} surface). */
  static final Pattern NUMERIC_RE = Pattern.compile("^[0-9]+$");

  /**
   * Plural-segment → singular Neo4j label. Includes both camelCase (v1
   * {@code /shepard/api/...} convention: {@code dataObjects},
   * {@code fileContainers}, …) and kebab-case (v2 {@code /v2/...} convention:
   * {@code data-objects}, {@code file-containers}, …) so a single map covers
   * both shelves.
   *
   * <p>Keys MUST be lowercased before lookup; the parser normalises before
   * consulting the map.
   */
  static final Map<String, String> PLURAL_TO_KIND = Map.ofEntries(
    // Core
    Map.entry("collections", "Collection"),
    Map.entry("dataobjects", "DataObject"),
    Map.entry("data-objects", "DataObject"),
    // Containers
    Map.entry("filecontainers", "FileContainer"),
    Map.entry("file-containers", "FileContainer"),
    Map.entry("timeseriescontainers", "TimeseriesContainer"),
    Map.entry("timeseries-containers", "TimeseriesContainer"),
    Map.entry("structureddatacontainers", "StructuredDataContainer"),
    Map.entry("structured-data-containers", "StructuredDataContainer"),
    // References
    Map.entry("references", "BasicReference"),
    Map.entry("filereferences", "FileReference"),
    Map.entry("file-references", "FileReference"),
    Map.entry("timeseriesreferences", "TimeseriesReference"),
    Map.entry("timeseries-references", "TimeseriesReference"),
    Map.entry("structureddatareferences", "StructuredDataReference"),
    Map.entry("structured-data-references", "StructuredDataReference"),
    Map.entry("urireferences", "URIReference"),
    Map.entry("uri-references", "URIReference"),
    Map.entry("dataobjectreferences", "DataObjectReference"),
    Map.entry("data-object-references", "DataObjectReference"),
    Map.entry("collectionreferences", "CollectionReference"),
    Map.entry("collection-references", "CollectionReference"),
    Map.entry("videoreferences", "VideoReference"),
    Map.entry("video-references", "VideoReference"),
    // File bundles + groups
    Map.entry("bundles", "FileBundle"),
    Map.entry("filebundles", "FileBundle"),
    Map.entry("file-bundles", "FileBundle"),
    Map.entry("filegroups", "FileGroup"),
    Map.entry("file-groups", "FileGroup"),
    // Semantics / lab journal / annotations
    Map.entry("semanticannotations", "SemanticAnnotation"),
    Map.entry("semantic-annotations", "SemanticAnnotation"),
    Map.entry("semanticrepositories", "SemanticRepository"),
    Map.entry("semantic-repositories", "SemanticRepository"),
    Map.entry("annotations", "Annotation"),
    Map.entry("labjournalentries", "LabJournalEntry"),
    Map.entry("lab-journal-entries", "LabJournalEntry"),
    // Fork additions (v2)
    Map.entry("snapshots", "Snapshot"),
    Map.entry("watches", "Watch"),
    Map.entry("notifications", "Notification"),
    Map.entry("templates", "ShepardTemplate"),
    Map.entry("shapes", "Shape"),
    Map.entry("properties", "CollectionProperties"),
    // Auth / users
    Map.entry("users", "User"),
    Map.entry("usergroups", "UserGroup"),
    Map.entry("user-groups", "UserGroup"),
    Map.entry("apikeys", "ApiKey"),
    Map.entry("api-keys", "ApiKey"),
    Map.entry("subscriptions", "Subscription"),
    // Misc
    Map.entry("activities", "Activity"),
    Map.entry("files", "ShepardFile")
  );

  /**
   * Parse the path and return the deepest {@code (kindPlural, id, isNumeric)}
   * pair found, or empty.
   *
   * <p>Walks segments right-to-left looking for {@code (knownPlural, idLike)}
   * where {@code idLike} matches UUID or pure-numeric. Stops on the first
   * match (the rightmost / deepest pair wins).
   *
   * @param path the request URI path (with or without a leading {@code /})
   */
  public static Optional<RawTarget> parse(String path) {
    if (path == null || path.isBlank()) return Optional.empty();
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    String[] segments = trimmed.split("/");
    // Need at least <kind>/<id> — two segments.
    if (segments.length < 2) return Optional.empty();

    // Walk right-to-left. For each segment at index i, treat it as a
    // candidate id and the segment at i-1 as a candidate kind plural.
    for (int i = segments.length - 1; i >= 1; i--) {
      String idCandidate = segments[i];
      Boolean numericFlag = null;
      if (UUID_RE.matcher(idCandidate).matches()) {
        numericFlag = false;
      } else if (NUMERIC_RE.matcher(idCandidate).matches()) {
        numericFlag = true;
      }
      if (numericFlag == null) continue;

      String pluralCandidate = segments[i - 1].toLowerCase();
      String kind = PLURAL_TO_KIND.get(pluralCandidate);
      if (kind == null) continue;

      return Optional.of(new RawTarget(kind, pluralCandidate, idCandidate, numericFlag));
    }
    return Optional.empty();
  }

  /**
   * A parsed path target — kind label + raw id string. The id is whatever the
   * path carried: a UUID (when {@code isNumeric=false}) or a pure-numeric
   * string (when {@code isNumeric=true}). The caller resolves the numeric form
   * to an appId via DAO lookup before stamping the {@link
   * de.dlr.shepard.provenance.entities.Activity}.
   *
   * @param kind       the singular Neo4j label (e.g. {@code "DataObject"})
   * @param plural     the raw path segment that produced the kind hint, lowercased
   *                   (kept for diagnostics; never used past resolution)
   * @param idString   the raw id segment as it appeared in the path
   * @param isNumeric  {@code true} when the id is a v1 numeric Neo4j id,
   *                   {@code false} when it's a UUID v7 appId
   */
  public record RawTarget(String kind, String plural, String idString, boolean isNumeric) {}
}

package de.dlr.shepard.provenance.filters;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Best-effort extraction of {@code (targetKind, targetAppId)} from a
 * REST request path. Used by {@link ProvenanceCaptureFilter} to stamp
 * the captured {@link de.dlr.shepard.provenance.entities.Activity}
 * with its target entity so the per-entity provenance drill-down
 * ({@code GET /v2/provenance/entity/{appId}}) can find rows.
 *
 * <p>Heuristic — never guarantees a target. Rules (in order):
 * <ol>
 *   <li>The path's last segment must be a UUID (RFC 4122). Anything
 *       else returns empty.</li>
 *   <li>The penultimate segment is taken as the kind hint and mapped
 *       to a {@code targetKind} via {@link #plural}. Unknown plurals
 *       yield the segment title-cased (so a future {@code /things/
 *       {uuid}} path lands {@code Thing}).</li>
 * </ol>
 *
 * <p>The legacy {@code /shepard/api/...} surface uses numeric ids,
 * not UUIDs; this resolver intentionally returns empty there. Only
 * the {@code /v2/...} shelf carries appId-shaped paths post-L2d.
 */
public final class TargetEntityResolver {

  private TargetEntityResolver() {}

  static final Pattern UUID_RE = Pattern.compile(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
  );

  /**
   * Returns the best-effort target reference, or empty if the path
   * doesn't end in a UUID segment.
   *
   * @param path the request URI path (with or without a leading {@code /})
   */
  public static Optional<TargetRef> resolve(String path) {
    if (path == null || path.isBlank()) return Optional.empty();
    String trimmed = path.startsWith("/") ? path.substring(1) : path;
    String[] segments = trimmed.split("/");
    if (segments.length < 1) return Optional.empty();
    String last = segments[segments.length - 1];
    if (!UUID_RE.matcher(last).matches()) return Optional.empty();
    String kindHint = segments.length >= 2 ? segments[segments.length - 2] : null;
    String kind = kindHint == null ? null : plural(kindHint);
    return Optional.of(new TargetRef(kind, last));
  }

  /**
   * Map a plural REST-path segment ("collections", "dataobjects", …)
   * to the singular Neo4j-label-shape ("Collection", "DataObject"). Falls
   * back to title-casing for unknown plurals so the resolver never
   * blanks the {@code targetKind}.
   */
  static String plural(String seg) {
    if (seg == null || seg.isBlank()) return null;
    String lower = seg.toLowerCase();
    return switch (lower) {
      case "collections" -> "Collection";
      case "dataobjects" -> "DataObject";
      case "references" -> "Reference";
      case "filebundles", "filereferences" -> "FileBundle";
      case "filegroups" -> "FileGroup";
      case "timeseries", "timeseriesreferences" -> "TimeseriesReference";
      case "structureddata", "structureddatareferences" -> "StructuredDataReference";
      case "videos", "videoreferences" -> "VideoReference";
      case "annotations" -> "Annotation";
      case "lab-journal-entries", "labjournalentries" -> "LabJournalEntry";
      case "users" -> "User";
      case "usergroups", "user-groups" -> "UserGroup";
      case "apikeys", "api-keys" -> "ApiKey";
      case "activities" -> "Activity";
      case "properties" -> "CollectionProperties";
      case "templates" -> "ShepardTemplate";
      default -> titleCase(stripTrailingS(lower));
    };
  }

  private static String stripTrailingS(String s) {
    if (s.endsWith("ies") && s.length() > 3) return s.substring(0, s.length() - 3) + "y";
    if (s.endsWith("s") && s.length() > 1) return s.substring(0, s.length() - 1);
    return s;
  }

  private static String titleCase(String s) {
    if (s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  /** A best-effort target-entity pointer derived from the request path. */
  public record TargetRef(String kind, String appId) {}
}

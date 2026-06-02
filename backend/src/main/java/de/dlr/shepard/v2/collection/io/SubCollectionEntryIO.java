package de.dlr.shepard.v2.collection.io;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Trimmed wire shape for a single sub-collection entry in
 * {@link SubCollectionsIO}. Contains only the fields needed for a
 * "collection browser" row — a full {@code CollectionIO} is available
 * via {@code GET /v2/collections/{appId}} when needed.
 *
 * <p>Field choices:
 * <ul>
 *   <li>{@code appId} — stable cross-substrate identifier (UUID v7).</li>
 *   <li>{@code id} — legacy Neo4j OGM long kept for upstream-compat clients.</li>
 *   <li>{@code name} — display name.</li>
 *   <li>{@code heroImage} — optional banner URL, may be {@code null}.</li>
 *   <li>{@code doCount} — DataObject count (useful for list card sizing).</li>
 *   <li>{@code lastActivity} — ISO-8601 timestamp of last mutation, may be
 *       {@code null} when no activities recorded yet.</li>
 *   <li>{@code ownerGroup} — optional display name for the owning group or
 *       user, may be {@code null}.</li>
 *   <li>{@code alsoMemberOf} — appIds of other parent projects this
 *       sub-collection is also a {@code urn:shepard:partOf} member of,
 *       excluding the current parent. Empty when the sub-collection has
 *       exactly one parent.</li>
 * </ul>
 *
 * <p>PROJ-REST-1 spec: {@code aidocs/integrations/121 §3.1}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SubCollectionEntry",
  description = "Trimmed summary of a sub-collection (child) of a parent Collection.")
public class SubCollectionEntryIO {

  @Schema(required = true, description = "Application-level identifier (UUID v7).")
  private String appId;

  @Schema(required = true, description = "Neo4j OGM long id (legacy; use appId for v2 calls).")
  private Long id;

  @Schema(required = true, description = "Collection display name.")
  private String name;

  @Schema(required = false, nullable = true,
    description = "Optional hero/banner image URL. Null when none is set.")
  private String heroImage;

  @Schema(required = true,
    description = "Number of DataObjects directly contained in this sub-collection.")
  private long doCount;

  @Schema(required = false, nullable = true,
    description = "ISO-8601 timestamp of the most recent Activity recorded for this " +
    "sub-collection. Null when no activities exist yet.")
  private String lastActivity;

  @Schema(required = false, nullable = true,
    description = "Display label for the owning principal (user or group). Null when unavailable.")
  private String ownerGroup;

  @Schema(required = true,
    description = "AppIds of other parent projects this sub-collection is also " +
    "a urn:shepard:partOf member of (excluding the current parent). Empty list when " +
    "the sub-collection has exactly one parent.")
  private List<String> alsoMemberOf;
}

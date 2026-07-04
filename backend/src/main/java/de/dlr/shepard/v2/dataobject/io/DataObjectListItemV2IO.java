package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2 list-endpoint projection of a {@link DataObject}.
 *
 * <p>Extends the shared {@link DataObjectIO} (which is also used by the
 * upstream-frozen {@code /shepard/api/} surface) with three per-kind
 * reference counts. Adding those counts directly to {@link DataObjectIO}
 * would change the upstream wire shape — forbidden by the API-version
 * policy in {@code CLAUDE.md}. This subclass lives in the {@code /v2/}
 * shelf only and is only referenced by {@link
 * de.dlr.shepard.v2.dataobject.resources.DataObjectV2Rest#list}.
 *
 * <p>Counts reflect non-deleted references only, matching the
 * post-load {@code cutDeleted()} filter applied by
 * {@link de.dlr.shepard.context.collection.services.DataObjectService}.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"id", "collectionId", "referenceIds", "successorIds",
  "predecessorIds", "childrenIds", "parentId", "incomingIds"})
@Schema(name = "DataObjectListItemV2", description = "DataObject list item enriched with per-kind reference counts (v2).")
@JsonFilter(DataObjectListItemV2IO.FILTER_ID)
public class DataObjectListItemV2IO extends DataObjectIO {

  /**
   * DB-OPT5 — Jackson filter id used by the {@code ?fields=} payload-diet
   * filter on {@code GET /v2/collections/{appId}/data-objects}. The filter
   * is registered per-request from a {@code Set<String>} of allowed flat
   * field names (no dotted paths). When the caller does not pass
   * {@code ?fields=}, the resource registers a default-trim filter that
   * drops the heavy nested fields (description, attributes, deprecated int
   * counts); when {@code ?include=full} is also passed, a serialize-all
   * filter is used instead.
   *
   * <p>See {@code aidocs/34-upstream-upgrade-path.md} row DB-OPT5 and the
   * design notes in {@code aidocs/16-dispatcher-backlog.md} row DB-OPT5.
   */
  public static final String FILTER_ID = "doListV2";

  @Schema(
    readOnly = true,
    description = "Number of non-deleted TimeseriesReferences attached to this DataObject."
  )
  private long timeseriesCount;

  @Schema(
    readOnly = true,
    description = "Number of non-deleted file references (FileReference + SingletonFileReference) attached to this DataObject."
  )
  private long fileCount;

  @Schema(
    readOnly = true,
    description = "Number of non-deleted StructuredDataReferences attached to this DataObject."
  )
  private long structuredDataCount;

  @Schema(
    readOnly = true,
    nullable = true,
    description =
      "Earliest data-point timestamp across all timeseries channels of this DataObject, " +
      "in nanoseconds since Unix epoch. Null when no timeseries data exists or " +
      "`?include=time-bounds` was not requested."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long timeBoundsStart;

  @Schema(
    readOnly = true,
    nullable = true,
    description =
      "Latest data-point timestamp across all timeseries channels of this DataObject, " +
      "in nanoseconds since Unix epoch. Null when no timeseries data exists or " +
      "`?include=time-bounds` was not requested."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Long timeBoundsEnd;

  /**
   * PROV1j — EU AI Act Art. 50 per-artefact visibility field.
   *
   * <p>Allowed values: {@code "human"}, {@code "ai"}, {@code "collaborative"}, or {@code null}
   * (semantically equivalent to {@code "human"} — the default human-authored case).
   * Omitted from JSON serialisation when {@code null}.
   */
  @Schema(
    readOnly = true,
    nullable = true,
    description =
      "EU AI Act Art. 50 provenance mode: 'human', 'ai', 'collaborative', or null (default, " +
      "equivalent to human-authored). Set from the X-AI-Agent request header on create.",
    enumeration = {"human", "ai", "collaborative"}
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String provenanceMode;

  /**
   * appId (UUID v7) of the parent DataObject, or {@code null} when this is a
   * top-level node. This is the appId-native replacement for the suppressed
   * numeric {@code parentId}: the collection sidebar tree links children to
   * parents purely by appId, so no numeric Neo4j id ever reaches the wire.
   * Omitted from JSON when {@code null} (top-level node).
   */
  @Schema(
    readOnly = true,
    nullable = true,
    description =
      "appId (UUID v7) of the parent DataObject, or null when top-level. " +
      "appId-native linkage for the sidebar tree (replaces the suppressed numeric parentId)."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String parentAppId;

  /**
   * appIds (UUID v7) of the direct, non-deleted child DataObjects (empty for a
   * leaf). The appId-native replacement for the suppressed numeric
   * {@code childrenIds}; drives the sidebar tree's expand affordance.
   */
  @Schema(
    readOnly = true,
    description =
      "appIds (UUID v7) of direct non-deleted children (empty for a leaf). " +
      "appId-native linkage for the sidebar tree (replaces the suppressed numeric childrenIds)."
  )
  private List<String> childrenAppIds = new ArrayList<>();

  /**
   * Copies all fields from {@code dataObject} (via the {@link DataObjectIO}
   * supertype constructor) and overlays the three reference counts plus the
   * appId-native parent/children linkage used by the sidebar tree.
   *
   * @param dataObject the loaded entity
   * @param tsCount    timeseries reference count (non-deleted)
   * @param fileCount  file reference count (non-deleted, both bundle and singleton kinds)
   * @param sdCount    structured-data reference count (non-deleted)
   */
  public DataObjectListItemV2IO(DataObject dataObject, long tsCount, long fileCount, long sdCount) {
    super(dataObject);
    this.timeseriesCount = tsCount;
    this.fileCount = fileCount;
    this.structuredDataCount = sdCount;
    // PROV1j — surface the stored provenance mode (null = human default).
    this.provenanceMode = dataObject.getProvenanceMode();

    // appId-native tree linkage (SIDEBAR-V2-APPID-LINK): the numeric id/parentId/
    // childrenIds are suppressed on the wire, so expose the parent + child appIds
    // the sidebar tree assembles on. Parent/children are already hydrated here
    // (DataObjectIO computes the numeric parentId/childrenIds from the same edges).
    if (dataObject.getParent() != null && !dataObject.getParent().isDeleted()) {
      this.parentAppId = dataObject.getParent().getAppId();
    }
    List<String> kids = new ArrayList<>();
    if (dataObject.getChildren() != null) {
      for (DataObject child : dataObject.getChildren()) {
        if (child != null && !child.isDeleted() && child.getAppId() != null) {
          kids.add(child.getAppId());
        }
      }
    }
    this.childrenAppIds = kids;
  }
}

package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
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
@JsonIgnoreProperties({
  // Neo4j internal node id — always suppressed on v2 (use appId).
  "id",
  // APISIMP-DO-IO-NUMERIC-ID-LEAK: inherited numeric Neo4j ids from DataObjectIO.
  // v2 clients address entities by appId (UUID v7), never by substrate-internal longs.
  "collectionId", "referenceIds", "successorIds", "predecessorIds",
  "childrenIds", "parentId", "incomingIds",
  // Deprecated int counts — superseded by timeseriesCount/fileCount/structuredDataCount
  // (long, non-deleted-only) declared in this class.
  "timeseriesReferenceCount", "fileBundleCount", "structuredDataReferenceCount"
})
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
   * Copies all fields from {@code dataObject} (via the {@link DataObjectIO}
   * supertype constructor) and overlays the three reference counts.
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
  }
}

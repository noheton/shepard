package de.dlr.shepard.v2.dataobject.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.io.DataObjectIO;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.file.entities.FileBundleReference;
import de.dlr.shepard.context.references.structureddata.entities.StructuredDataReference;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import io.quarkus.logging.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REF-1 — enriched single-DataObject response for
 * {@code GET /v2/collections/{cid}/data-objects/{did}}.
 *
 * <p>Extends the upstream-frozen {@link DataObjectIO} (which MUST NOT be
 * modified per the API-version policy) with typed per-kind container lists
 * and compact summary objects for related DataObjects.
 *
 * <p>Legacy fields from {@link DataObjectIO} ({@code referenceIds[]},
 * {@code successorIds[]}, etc.) are retained for backward compatibility.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties({"id", "collectionId", "referenceIds", "successorIds",
  "predecessorIds", "childrenIds", "parentId", "incomingIds"})
@Schema(
  name = "DataObjectDetail",
  description = "Full DataObject detail response, extends DataObjectIO with typed " +
    "container lists and compact predecessor/successor/parent/child summaries."
)
public class DataObjectDetailV2IO extends DataObjectIO {

  /**
   * Typed breakdown of the containers attached to this DataObject,
   * split by kind.
   */
  @Schema(readOnly = true, description = "Typed container lists by kind.")
  private Containers containers = new Containers();

  /**
   * Compact summaries of all direct predecessors (appId, id, name, status).
   */
  @Schema(readOnly = true, description = "Direct predecessors of this DataObject.")
  private List<DataObjectSummaryIO> predecessorSummaries = new ArrayList<>();

  /**
   * PROV1k — typed predecessor relationships for this DataObject.
   *
   * <p>Each entry pairs a compact predecessor summary with the PROV-O / FAIR²R
   * relationship type stored in {@code typedPredecessorsJson}. When the stored
   * JSON is null (pre-PROV1k DataObjects) all entries default to
   * {@code "prov:wasInformedBy"} for backward compatibility.
   *
   * <p>Null (omitted from JSON) when the DataObject has no predecessors.
   */
  @Schema(
    readOnly = true,
    nullable = true,
    description =
      "PROV1k — typed predecessor list. Null when no predecessors. " +
      "Each entry carries the predecessor's summary (appId, id, name, status) " +
      "and the PROV-O / FAIR²R relationship type. " +
      "Pre-PROV1k predecessors default to 'prov:wasInformedBy'."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<TypedPredecessorSummaryIO> typedPredecessorSummaries;

  /**
   * Compact summaries of all direct successors (appId, id, name, status).
   */
  @Schema(readOnly = true, description = "Direct successors of this DataObject.")
  private List<DataObjectSummaryIO> successorSummaries = new ArrayList<>();

  /**
   * Compact summaries of all direct children (appId, id, name, status).
   */
  @Schema(readOnly = true, description = "Direct children of this DataObject.")
  private List<DataObjectSummaryIO> childSummaries = new ArrayList<>();

  /**
   * Compact summary of the parent DataObject, or {@code null} if this is a
   * top-level DataObject.
   */
  @Schema(readOnly = true, nullable = true, description = "Parent DataObject summary, or null if top-level.")
  private DataObjectSummaryIO parentSummary;

  /**
   * PROV1j — EU AI Act Art. 50 per-artefact visibility field.
   *
   * <p>Mirrors {@link de.dlr.shepard.context.collection.entities.DataObject#getProvenanceMode()}.
   * Allowed values: {@code "human"}, {@code "ai"}, {@code "collaborative"}, or {@code null}
   * (semantically equivalent to {@code "human"} — the default human-authored case).
   * Omitted from JSON serialisation when {@code null}.
   */
  @Schema(
    readOnly = true,
    nullable = true,
    description =
      "EU AI Act Art. 50 provenance mode for this DataObject: 'human', 'ai', 'collaborative', " +
      "or null (default, semantically equivalent to human-authored). " +
      "Set automatically from the X-AI-Agent request header on create when not provided explicitly.",
    enumeration = {"human", "ai", "collaborative"}
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String provenanceMode;

  /**
   * API1 — appIds (UUID v7) of the {@code :TimeseriesReference} nodes attached to
   * this DataObject. Use a value here to navigate to the linked timeseries container
   * via {@code GET /v2/containers/{appId}}. Null (omitted from JSON) when
   * no timeseries references exist.
   *
   * <p>Unlike the parent-class {@link DataObjectIO#getReferenceIds()} long array —
   * which mixes all reference node OGM IDs across all kinds — these per-kind appId
   * lists are type-safe, stable across DB migrations, and directly usable by MCP
   * agents and REST clients without further resolution.
   */
  @Schema(
    readOnly = true,
    nullable = true,
    description = "appIds (UUID v7) of TimeseriesReference nodes on this DataObject. " +
      "Use with GET /v2/containers/{appId} to reach the container. " +
      "Null when none exist."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> timeseriesReferenceAppIds;

  /**
   * API1 — appIds (UUID v7) of the {@code :FileBundleReference} nodes. Null when none.
   */
  @Schema(
    readOnly = true,
    nullable = true,
    description = "appIds (UUID v7) of FileBundleReference nodes on this DataObject. " +
      "Null when none exist."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> fileReferenceAppIds;

  /**
   * API1 — appIds (UUID v7) of the {@code :StructuredDataReference} nodes. Null when none.
   */
  @Schema(
    readOnly = true,
    nullable = true,
    description = "appIds (UUID v7) of StructuredDataReference nodes on this DataObject. " +
      "Null when none exist."
  )
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private List<String> structuredDataReferenceAppIds;

  public DataObjectDetailV2IO(DataObject dataObject) {
    super(dataObject);

    // Build typed container lists from the reference list already loaded
    // by DataObjectIO's constructor (which walks dataObject.getReferences()).
    List<ContainerRefIO> timeseries = new ArrayList<>();
    List<ContainerRefIO> files = new ArrayList<>();
    List<ContainerRefIO> structuredData = new ArrayList<>();

    for (BasicReference ref : dataObject.getReferences()) {
      if (ref.isDeleted()) continue;

      String refAppId = ref.getAppId();

      if (ref instanceof TimeseriesReference tr) {
        if (tr.getTimeseriesContainer() != null) {
          timeseries.add(new ContainerRefIO(
            tr.getTimeseriesContainer().getAppId(),
            tr.getTimeseriesContainer().getName(),
            refAppId
          ));
        }
      } else if (ref instanceof FileBundleReference fbr) {
        if (fbr.getFileContainer() != null) {
          files.add(new ContainerRefIO(
            fbr.getFileContainer().getAppId(),
            fbr.getFileContainer().getName(),
            refAppId
          ));
        }
      } else if (ref instanceof StructuredDataReference sdr) {
        if (sdr.getStructuredDataContainer() != null) {
          structuredData.add(new ContainerRefIO(
            sdr.getStructuredDataContainer().getAppId(),
            sdr.getStructuredDataContainer().getName(),
            refAppId
          ));
        }
      }
    }

    this.containers = new Containers(timeseries, files, structuredData);

    // Predecessor / successor / child summaries.
    for (DataObject p : dataObject.getPredecessors()) {
      if (!p.isDeleted()) predecessorSummaries.add(new DataObjectSummaryIO(p));
    }
    for (DataObject s : dataObject.getSuccessors()) {
      if (!s.isDeleted()) successorSummaries.add(new DataObjectSummaryIO(s));
    }
    for (DataObject c : dataObject.getChildren()) {
      if (!c.isDeleted()) childSummaries.add(new DataObjectSummaryIO(c));
    }
    if (dataObject.getParent() != null && !dataObject.getParent().isDeleted()) {
      this.parentSummary = new DataObjectSummaryIO(dataObject.getParent());
    }

    // PROV1j — surface the stored provenance mode (null = human default).
    this.provenanceMode = dataObject.getProvenanceMode();

    // PROV1k — build typed predecessor summary list.
    // Parse typedPredecessorsJson to get per-predecessor relationship types,
    // then cross-reference with the loaded predecessor DataObject list.
    if (!dataObject.getPredecessors().isEmpty()) {
      Map<String, String> appIdToType = parseTypedPredecessorsJson(dataObject.getTypedPredecessorsJson());
      List<TypedPredecessorSummaryIO> tpList = new ArrayList<>();
      for (DataObject p : dataObject.getPredecessors()) {
        if (p.isDeleted()) continue;
        String rType = appIdToType.getOrDefault(p.getAppId(), TypedPredecessorIO.DEFAULT_TYPE);
        tpList.add(new TypedPredecessorSummaryIO(
          p.getAppId(),
          p.getShepardId() != null ? p.getShepardId() : -1L,
          p.getName(),
          p.getStatus(),
          rType
        ));
      }
      this.typedPredecessorSummaries = tpList.isEmpty() ? null : tpList;
    }
  }

  // ── helper ───────────────────────────────────────────────────────────────

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * PROV1k — parse the {@code typedPredecessorsJson} string stored on the
   * DataObject node into a map of {@code appId → relationshipType}.
   *
   * <p>Returns an empty map when the stored JSON is null, blank, or malformed.
   * Malformed JSON is logged at WARN level and treated as "no typed entries"
   * so callers fall back to the default {@code "prov:wasInformedBy"}.
   */
  static Map<String, String> parseTypedPredecessorsJson(String json) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      List<TypedPredecessorIO> list = MAPPER.readValue(
        json,
        new TypeReference<List<TypedPredecessorIO>>() {}
      );
      Map<String, String> result = new HashMap<>();
      for (TypedPredecessorIO tp : list) {
        if (tp.predecessorAppId() != null) {
          result.put(tp.predecessorAppId(), tp.effectiveRelationshipType());
        }
      }
      return result;
    } catch (Exception e) {
      Log.warnf("PROV1k: failed to parse typedPredecessorsJson — falling back to defaults. %s", e.getMessage());
      return Map.of();
    }
  }

  // ── inner class ──────────────────────────────────────────────────────────

  /**
   * Typed container lists for timeseries, file, and structured-data kinds.
   */
  @Data
  @NoArgsConstructor
  @Schema(name = "DataObjectContainers", description = "Per-kind container references.")
  public static class Containers {

    @Schema(readOnly = true, description = "Timeseries container references (one per TimeseriesReference).")
    private List<ContainerRefIO> timeseries = new ArrayList<>();

    @Schema(readOnly = true, description = "File container references (one per FileBundleReference).")
    private List<ContainerRefIO> files = new ArrayList<>();

    @Schema(readOnly = true, description = "Structured-data container references (one per StructuredDataReference).")
    private List<ContainerRefIO> structuredData = new ArrayList<>();

    public Containers(List<ContainerRefIO> timeseries, List<ContainerRefIO> files, List<ContainerRefIO> structuredData) {
      this.timeseries = timeseries;
      this.files = files;
      this.structuredData = structuredData;
    }
  }
}
